#include <linux/anon_inodes.h>
#include <linux/err.h>
#include <linux/fdtable.h>
#include <linux/file.h>
#include <linux/fs.h>
#include <linux/kprobes.h>
#include <linux/pid.h>
#include <linux/slab.h>
#include <linux/syscalls.h>
#include <linux/task_work.h>
#include <linux/uaccess.h>
#include <linux/version.h>
#include <linux/utsname.h> // utsname() and uts_sem

#include "uapi/supercall.h"
#include "supercall/internal.h"
#include "arch.h"
#include "util.h"
#include "klog.h" // IWYU pragma: keep
#include "manager/manager_identity.h"

#include "sulog/event.h"

uint32_t ksuver_override = 0;

struct ksu_install_fd_tw {
    struct callback_head cb;
    int __user *outp;
};

static int anon_ksu_release(struct inode *inode, struct file *filp)
{
    pr_info("ksu fd released\n");
    return 0;
}

static long anon_ksu_ioctl(struct file *filp, unsigned int cmd, unsigned long arg)
{
    return ksu_supercall_handle_ioctl(cmd, (void __user *)arg);
}

static const struct file_operations anon_ksu_fops = {
    .owner = THIS_MODULE,
    .unlocked_ioctl = anon_ksu_ioctl,
    .compat_ioctl = anon_ksu_ioctl,
    .release = anon_ksu_release,
};

int ksu_install_fd(void)
{
    struct file *filp;
    int fd;

    fd = get_unused_fd_flags(O_CLOEXEC);
    if (fd < 0) {
        pr_err("ksu_install_fd: failed to get unused fd\n");
        return fd;
    }

    filp = anon_inode_getfile("[ksu_driver]", &anon_ksu_fops, NULL, O_RDWR | O_CLOEXEC);
    if (IS_ERR(filp)) {
        pr_err("ksu_install_fd: failed to create anon inode file\n");
        put_unused_fd(fd);
        return PTR_ERR(filp);
    }

    fd_install(fd, filp);
    pr_info("ksu fd installed: %d for pid %d\n", fd, current->pid);
    return fd;
}

static void ksu_install_fd_tw_func(struct callback_head *cb)
{
    struct ksu_install_fd_tw *tw = container_of(cb, struct ksu_install_fd_tw, cb);
    int fd = ksu_install_fd();

    pr_info("[%d] install ksu fd: %d\n", current->pid, fd);
    if (copy_to_user(tw->outp, &fd, sizeof(fd))) {
        pr_err("install ksu fd reply err\n");
        ksu_close_fd(fd);
    }

    kfree(tw);
}

int ksu_supercall_change_manager_uid(__u32 uid)
{
    if (current_uid().val != 0)
        return -EPERM;
    pr_info("ksu_supercall: set manager appid to: %d\n", uid);
    ksu_set_manager_appid(uid);
    return 0;
}
int ksu_supercall_change_ksuver(__u32 version)
{
    if (current_uid().val != 0)
        return -EPERM;
    pr_info("ksu_supercall: change ksuver to: %d\n", version);
    ksuver_override = version;
    return 0;
}
int ksu_supercall_spoof_uname(const void __user *user_data)
{
    char release_buf[65];
    char version_buf[65];
    static char original_release_buf[65] = {0};
    static char original_version_buf[65] = {0};
    if (current_uid().val != 0)
        return -EPERM;
    if (!user_data)
        return -EINVAL;
    // user_data points to "release\0version\0" in user space
    if (strncpy_from_user(release_buf, (char __user *)user_data, sizeof(release_buf)) < 0)
        return -EFAULT;
    release_buf[sizeof(release_buf) - 1] = '\0';
    if (strncpy_from_user(version_buf, (char __user *)(user_data + strlen(release_buf) + 1),
                          sizeof(version_buf)) < 0)
        return -EFAULT;
    version_buf[sizeof(version_buf) - 1] = '\0';
    if (original_release_buf[0] == '\0') {
        struct new_utsname *u_curr = utsname();
        // we save current version as the original before modifying
        strscpy(original_release_buf, u_curr->release, sizeof(original_release_buf));
        strscpy(original_version_buf, u_curr->version, sizeof(original_version_buf));
        pr_info("ksu_supercall: original uname saved: %s %s\n", original_release_buf,
                original_version_buf);
    }
    // so user can reset
    if (!strcmp(release_buf, "default") || !strcmp(version_buf, "default")) {
        memcpy(release_buf, original_release_buf, sizeof(release_buf));
        memcpy(version_buf, original_version_buf, sizeof(version_buf));
    }
    pr_info("ksu_supercall: spoofing kernel to: %s - %s\n", release_buf, version_buf);
    {
        struct new_utsname *u = utsname();
        down_write(&uts_sem);
        strscpy(u->release, release_buf, sizeof(u->release));
        strscpy(u->version, version_buf, sizeof(u->version));
        up_write(&uts_sem);
    }
    return 0;
}
int ksu_supercall_sulog_compat_dump(void __user *uptr)
{
    if (current_uid().val != 0)
        return -EPERM;
    return ksu_sulog_handle_compat_dump(uptr);
}
static int reboot_handler_pre(struct kprobe *p, struct pt_regs *regs)
{
    struct pt_regs *real_regs = PT_REAL_REGS(regs);
    int magic1 = (int)PT_REGS_PARM1(real_regs);
    int magic2 = (int)PT_REGS_PARM2(real_regs);
    unsigned long arg4 = (unsigned long)PT_REGS_SYSCALL_PARM4(real_regs);
    /* This kprobe now ONLY serves the KSU fd install channel.
     * All supercalls (CHANGE_MANAGER_UID / CHANGE_KSUVER / CHANGE_SPOOF_UNAME /
     * GET_SULOG_DUMP_V2) have been migrated to ioctl on the ksu_driver fd. */
    if (magic1 == KSU_INSTALL_MAGIC1 && magic2 == KSU_INSTALL_MAGIC2) {
        struct ksu_install_fd_tw *tw;
        tw = kzalloc(sizeof(*tw), GFP_ATOMIC);
        if (!tw)
            return 0;
        tw->outp = (int __user *)arg4;
        tw->cb.func = ksu_install_fd_tw_func;
        if (task_work_add(current, &tw->cb, TWA_RESUME)) {
            kfree(tw);
            pr_warn("install fd add task_work failed\n");
        }
    }
    return 0;
}
static struct kprobe reboot_kp = {
    .symbol_name = REBOOT_SYMBOL,
    .pre_handler = reboot_handler_pre,
};

void __init ksu_supercalls_init(void)
{
    int rc;

    ksu_supercall_dump_commands();

    rc = register_kprobe(&reboot_kp);
    if (rc) {
        pr_err("reboot kprobe failed: %d\n", rc);
    } else {
        pr_info("reboot kprobe registered successfully\n");
    }
}

void __exit ksu_supercalls_exit(void)
{
    unregister_kprobe(&reboot_kp);
    ksu_supercall_cleanup_state();
}

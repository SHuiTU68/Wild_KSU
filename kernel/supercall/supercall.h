#ifndef __KSU_H_SUPERCALL
#define __KSU_H_SUPERCALL

#include <linux/types.h>
#include <linux/uaccess.h>

// IOCTL handler types
typedef int (*ksu_ioctl_handler_t)(void __user *arg);
typedef bool (*ksu_perm_check_t)(void);

// IOCTL command mapping
struct ksu_ioctl_cmd_map {
    unsigned int cmd;
    const char *name;
    ksu_ioctl_handler_t handler;
    ksu_perm_check_t perm_check; // Permission check function
};

// Install KSU fd to current process
int ksu_install_fd(void);
// Toolkit supercalls (ioctl backend, root only, no kprobe)
int ksu_supercall_change_manager_uid(__u32 uid);
int ksu_supercall_change_ksuver(__u32 version);
int ksu_supercall_spoof_uname(const void __user *user_data);
int ksu_supercall_sulog_compat_dump(void __user *uptr);

void ksu_supercalls_init(void);
void ksu_supercalls_exit(void);
#endif // __KSU_H_SUPERCALL

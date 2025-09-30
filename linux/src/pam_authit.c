#include <security/pam_modules.h>
#include <security/pam_ext.h> 
#include <stdlib.h>
#include <unistd.h>
#include <sys/wait.h>
#include <syslog.h> 
#include <string.h> 
#include <pwd.h>    
#include <stdio.h>  
#define PYTHON_EXEC "/bin/python3"
#define SCRIPT_PATH "/usr/local/lib/pam_authit/main.py"

PAM_EXTERN int pam_sm_authenticate(pam_handle_t *pamh, int flags, int argc, const char **argv) {
    const char* username;
    struct passwd *pwd;
    pid_t pid;
    int status;

    if (pam_get_user(pamh, &username, NULL) != PAM_SUCCESS || !username) {
        pam_syslog(pamh, LOG_ERR, "can't find any username.");
        return PAM_AUTH_ERR;
    }

    pwd = getpwnam(username);
    if (pwd == NULL) {
        pam_syslog(pamh, LOG_ERR, "could not find user details for '%s'.", username);
        return PAM_AUTH_ERR;
    }

    if (strpbrk(username, ";&|<>`!$()\\") != NULL) {
        pam_syslog(pamh, LOG_ERR, "username '%s' contains forbidden characters.", username);
        return PAM_AUTH_ERR;
    }

    if (access(PYTHON_EXEC, X_OK) != 0) {
        pam_syslog(pamh, LOG_ERR, "python interpreter '%s' is not executable or does not exist.", PYTHON_EXEC);
        return PAM_AUTH_ERR;
    }

    pid = fork();
    if (pid == -1) {
        pam_syslog(pamh, LOG_ERR, "Failed to fork process: %m"); 
        return PAM_AUTH_ERR;
    }

    if (pid == 0) { 
        if (setgid(pwd->pw_gid) != 0) {
            pam_syslog(pamh, LOG_ERR, "could not set group ID for user %s", username);
            _exit(127);
        }
        if (setuid(pwd->pw_uid) != 0) {
            pam_syslog(pamh, LOG_ERR, "could not set user ID for user %s", username);
            _exit(127);
        }

        char *const script_argv[] = { (char*)PYTHON_EXEC, (char*)SCRIPT_PATH, (char*)username, NULL };
        
        char home_env[256];
        snprintf(home_env, sizeof(home_env), "HOME=%s", pwd->pw_dir);
        
        char *const script_envp[] = {
            "PATH=/usr/bin:/bin",
            home_env,
            NULL
        };

        execve(PYTHON_EXEC, script_argv, script_envp);
        _exit(127); 
    }

    if (waitpid(pid, &status, 0) == -1) {
        pam_syslog(pamh, LOG_ERR, "error waiting for the process: %m");
        return PAM_AUTH_ERR;
    }

    if (WIFEXITED(status) && WEXITSTATUS(status) == 0) {
        pam_syslog(pamh, LOG_INFO, "script successfully authenticated user '%s'.", username);
        return PAM_SUCCESS;
    } else {
        pam_syslog(pamh, LOG_WARNING, "script failed to auth user '%s' (exit status: %d).",
                   username, WEXITSTATUS(status));
        return PAM_AUTH_ERR;
    }
}

PAM_EXTERN int pam_sm_setcred(pam_handle_t *pamh, int flags, int argc, const char **argv) {
    return PAM_SUCCESS;
}
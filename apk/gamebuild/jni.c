// APK-installed executable boundary: never execute from writable app storage.
#define _GNU_SOURCE
#include <jni.h>
#include <pthread.h>
#include <sys/wait.h>
#include <sys/prctl.h>
#include <unistd.h>
#include <fcntl.h>
#include <signal.h>
#include <errno.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
static pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;
static pid_t active;
static int cancelled;
JNIEXPORT void JNICALL Java_com_letsgorecomp_GameBuild_begin(JNIEnv *e, jclass c) {
    (void)e; (void)c;
    pthread_mutex_lock(&mutex);
    if (!active) cancelled = 0;
    pthread_mutex_unlock(&mutex);
}
JNIEXPORT void JNICALL Java_com_letsgorecomp_GameBuild_cancel(JNIEnv *e, jclass c) {
    (void)e; (void)c;
    pthread_mutex_lock(&mutex);
    cancelled = 1;
    if (active > 0) kill(-active, SIGKILL);
    pthread_mutex_unlock(&mutex);
}
JNIEXPORT jint JNICALL Java_com_letsgorecomp_GameBuild_execute(JNIEnv *e, jclass c,
        jbyteArray je, jbyteArray js, jbyteArray jr, jbyteArray jw, jbyteArray jl) {
    (void)c;
    const char *exe = (char *)(*e)->GetByteArrayElements(e, je, 0);
    const char *script = (char *)(*e)->GetByteArrayElements(e, js, 0);
    const char *root = (char *)(*e)->GetByteArrayElements(e, jr, 0);
    const char *work = (char *)(*e)->GetByteArrayElements(e, jw, 0);
    const char *lib = (char *)(*e)->GetByteArrayElements(e, jl, 0);
    int result = 255;
    if (!exe || !script || !root || !work || !lib) goto done;
    char home[4096], path[8192];
    snprintf(home, sizeof home, "%s/kit/pyhome", work);
    snprintf(path, sizeof path, "%s/kit/tools:%s/kit/pyhome/lib/python3.14/site-packages", work, work);
    setenv("LASPY_HOME", home, 1); setenv("LASPY_PATH", path, 1);
    setenv("LIBUNICORN_PATH", lib, 1); setenv("LIBCAPSTONE_PATH", lib, 1); setenv("TMPDIR", work, 1);
    char log[4096]; snprintf(log, sizeof log, "%s/builder.log", work);
    int fd = open(log, O_CREAT | O_TRUNC | O_WRONLY | O_CLOEXEC, 0600);
    if (fd < 0) goto done;
    const char *args[] = {exe, script, root, work, lib, NULL};
    pthread_mutex_lock(&mutex);
    if (active || cancelled) { pthread_mutex_unlock(&mutex); close(fd); goto done; }
    pid_t parent = getpid();
    pid_t pid = fork();
    if (!pid) {
        // No heap/stdio/JNI after fork of this multithreaded process.
        if (setpgid(0, 0) || prctl(PR_SET_PDEATHSIG, SIGKILL) || getppid() != parent
                || dup2(fd, 1) < 0 || dup2(fd, 2) < 0) _exit(126);
        close(fd);
        execv(exe, (char *const *)args);
        _exit(127);
    }
    close(fd);
    if (pid > 0) { setpgid(pid, pid); active = pid; }
    pthread_mutex_unlock(&mutex);
    if (pid > 0) {
        int status = 0; pid_t got;
        do { got = waitpid(pid, &status, 0); } while (got < 0 && errno == EINTR);
        pthread_mutex_lock(&mutex);
        // Reap compiler descendants through Python in normal completion; on
        // cancellation stop the whole process group, not just the interpreter.
        kill(-pid, SIGKILL); active = 0;
        result = cancelled ? 130 : got < 0 ? 255 : WIFEXITED(status) ? WEXITSTATUS(status) : 128 + WTERMSIG(status);
        pthread_mutex_unlock(&mutex);
    }
 done:
    if (exe) (*e)->ReleaseByteArrayElements(e, je, (jbyte *)exe, JNI_ABORT);
    if (script) (*e)->ReleaseByteArrayElements(e, js, (jbyte *)script, JNI_ABORT);
    if (root) (*e)->ReleaseByteArrayElements(e, jr, (jbyte *)root, JNI_ABORT);
    if (work) (*e)->ReleaseByteArrayElements(e, jw, (jbyte *)work, JNI_ABORT);
    if (lib) (*e)->ReleaseByteArrayElements(e, jl, (jbyte *)lib, JNI_ABORT);
    return result;
}

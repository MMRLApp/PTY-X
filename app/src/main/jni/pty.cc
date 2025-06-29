#include <jni.h>
#include <unistd.h>
#include <pty.h>
#include <fcntl.h>
#include <string>
#include <thread>
#include <vector>
#include <atomic>
#include <mutex>
#include <android/log.h>
#include <sys/wait.h>
#include <sys/ioctl.h>
#include <termios.h>

static JavaVM* jvm = nullptr;
static jobject eventListener = nullptr;
static jmethodID onDataMethod = nullptr;
static jmethodID onExitMethod = nullptr;

struct PtyInstance {
    int master_fd = -1;
    pid_t pid = -1;
    std::thread read_thread;
    std::atomic<bool> running{false};
};

void notify_data(JNIEnv* env, const char* data, size_t length) {
    if (!eventListener || !onDataMethod) return;

    jbyteArray jdata = env->NewByteArray(length);
    env->SetByteArrayRegion(jdata, 0, length, (const jbyte*)data);
    env->CallVoidMethod(eventListener, onDataMethod, jdata);
    env->DeleteLocalRef(jdata);
}

void notify_exit(JNIEnv* env, int exitCode) {
    if (!eventListener || !onExitMethod) return;
    env->CallVoidMethod(eventListener, onExitMethod, exitCode);
}

extern "C" JNIEXPORT jlong JNICALL
Java_dev_mmrl_wxu_PtyImpl_nativeStart(JNIEnv *env, jobject thiz,
                                jstring shell, jobjectArray args,
                                jobjectArray env_vars,
                                jint cols, jint rows) {
    const char* shell_path = env->GetStringUTFChars(shell, nullptr);

    // Convert Java String[] to char*[]
    int argc = env->GetArrayLength(args);
    char** argv = new char*[argc + 2];
    argv[0] = strdup(shell_path);
    for (int i = 0; i < argc; i++) {
        auto arg = (jstring)env->GetObjectArrayElement(args, i);
        const char* carg = env->GetStringUTFChars(arg, nullptr);
        argv[i+1] = strdup(carg);
        env->ReleaseStringUTFChars(arg, carg);
    }
    argv[argc+1] = nullptr;

    // Convert environment variables
    int envc = env->GetArrayLength(env_vars);
    char** envp = new char*[envc + 1];
    for (int i = 0; i < envc; i++) {
        auto env_var = (jstring)env->GetObjectArrayElement(env_vars, i);
        const char* cenv = env->GetStringUTFChars(env_var, nullptr);
        envp[i] = strdup(cenv);
        env->ReleaseStringUTFChars(env_var, cenv);
    }
    envp[envc] = nullptr;

    // Create PTY
    winsize size = {static_cast<unsigned short>(rows), static_cast<unsigned short>(cols), 0, 0};
    int master_fd;
    pid_t pid = forkpty(&master_fd, nullptr, nullptr, &size);

    if (pid < 0) {
        // Cleanup
        for (int i = 0; i < argc+1; i++) free(argv[i]);
        delete[] argv;
        for (int i = 0; i < envc; i++) free(envp[i]);
        delete[] envp;
        env->ReleaseStringUTFChars(shell, shell_path);
        return 0;
    }

    if (pid == 0) { // Child process
        execve(shell_path, argv, envp);
        _exit(1); // Only reached if exec fails
    }

    // Parent process
    auto* instance = new PtyInstance();
    instance->master_fd = master_fd;
    instance->pid = pid;
    instance->running = true;

    // Store JVM reference for callbacks
    env->GetJavaVM(&jvm);

    // Start read thread
    instance->read_thread = std::thread([instance]() {
        JNIEnv* env;
        jvm->AttachCurrentThread(&env, nullptr);

        char buffer[4096];
        while (instance->running) {
            ssize_t count = read(instance->master_fd, buffer, sizeof(buffer));
            if (count > 0) {
                notify_data(env, buffer, count);
            } else {
                break;
            }
        }

        // Process exited
        int status;
        waitpid(instance->pid, &status, 0);
        int exitCode = WIFEXITED(status) ? WEXITSTATUS(status) : -1;
        notify_exit(env, exitCode);

        instance->running = false;
        jvm->DetachCurrentThread();
    });

    // Cleanup
    for (int i = 0; i < argc+1; i++) free(argv[i]);
    delete[] argv;
    for (int i = 0; i < envc; i++) free(envp[i]);
    delete[] envp;
    env->ReleaseStringUTFChars(shell, shell_path);

    return (jlong)instance;
}

extern "C" JNIEXPORT void JNICALL
Java_dev_mmrl_wxu_PtyImpl_nativeResize(JNIEnv *env, jobject thiz, jlong handle, jint cols, jint rows) {
    auto* instance = (PtyInstance*)handle;
    if (!instance || !instance->running) return;

    winsize size = {static_cast<unsigned short>(rows), static_cast<unsigned short>(cols), 0, 0};
    ioctl(instance->master_fd, TIOCSWINSZ, &size);
}

extern "C" JNIEXPORT void JNICALL
Java_dev_mmrl_wxu_PtyImpl_nativeWrite(JNIEnv *env, jobject thiz, jlong handle, jbyteArray data) {
    auto* instance = (PtyInstance*)handle;
    if (!instance || !instance->running) return;

    jsize length = env->GetArrayLength(data);
    jbyte* bytes = env->GetByteArrayElements(data, nullptr);
    write(instance->master_fd, bytes, length);
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);
}

extern "C" JNIEXPORT void JNICALL
Java_dev_mmrl_wxu_PtyImpl_nativeKill(JNIEnv *env, jobject thiz, jlong handle) {
    auto* instance = (PtyInstance*)handle;
    if (!instance) return;

    instance->running = false;
    if (instance->pid > 0) {
        kill(instance->pid, SIGKILL);
        instance->pid = -1;
    }
    if (instance->master_fd >= 0) {
        close(instance->master_fd);
        instance->master_fd = -1;
    }
    if (instance->read_thread.joinable()) {
        instance->read_thread.join();
    }
    delete instance;
}

extern "C" JNIEXPORT void JNICALL
Java_dev_mmrl_wxu_PtyImpl_nativeSetEventListener(JNIEnv *env, jobject thiz, jobject listener) {
    if (eventListener) {
        env->DeleteGlobalRef(eventListener);
    }

    eventListener = env->NewGlobalRef(listener);
    jclass listenerClass = env->GetObjectClass(listener);
    onDataMethod = env->GetMethodID(listenerClass, "onData", "([B)V");
    onExitMethod = env->GetMethodID(listenerClass, "onExit", "(I)V");
    env->DeleteLocalRef(listenerClass);
}
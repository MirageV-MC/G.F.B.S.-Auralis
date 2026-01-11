package org.mirage.gfbs_auralis;
/**
 * G.F.B.S.-Auralis (gfbs_auralis) - A Minecraft Mod
 * Copyright (C) 2025-2029 Mirage-MC
 * <p>
 * This program is licensed under the MIT License.
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software
 * is provided to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all copies
 * or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 * FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.openal.*;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.openal.AL10.*;

@Mod.EventBusSubscriber(
        modid = GFBsAuralis.MODID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT
)
public final class AuralisAL implements AutoCloseable {
    public static final AtomicReference<AuralisAL> GLOBAL = new AtomicReference<>(null);

    public static final class Config {
        /** OpenAL device name, or null for default device. */
        public final String deviceName;

        /** Thread name for the OpenAL thread. */
        public final String threadName;

        /** If true, make the OpenAL thread a daemon thread. */
        public final boolean daemonThread;

        /**
         * ALC context attributes array (ALC10.alcCreateContext attrs), or null for default.
         * Example for explicit attrs:
         *   new int[] { ALC_REFRESH, 60, ALC_SYNC, ALC_FALSE, 0 }
         */
        public final int[] contextAttributes;

        /**
         * Poll wait time for loop when no tasks are queued.
         * Smaller = lower latency, higher CPU wakeups.
         */
        public final long idleWaitMillis;

        /**
         * If true, do extra checks / throw more aggressively.
         * In release builds you can keep it true as well; overhead is usually small.
         */
        public final boolean strictChecks;

        public Config(
                String deviceName,
                String threadName,
                boolean daemonThread,
                int[] contextAttributes,
                long idleWaitMillis,
                boolean strictChecks
        ) {
            this.deviceName = deviceName;
            this.threadName = Objects.requireNonNullElse(threadName, "Auralis-OpenAL");
            this.daemonThread = daemonThread;
            this.contextAttributes = contextAttributes; // may be null
            this.idleWaitMillis = Math.max(0L, idleWaitMillis);
            this.strictChecks = strictChecks;
        }

        public static Config defaults() {
            return new Config(
                    null,
                    "Auralis-OpenAL",
                    true,
                    null,
                    2L,
                    true
            );
        }
    }

    private interface ALTask {
        void run() throws Throwable;
    }

    private static final class TaskWrapper implements ALTask {
        private final Runnable runnable;
        TaskWrapper(Runnable runnable) { this.runnable = runnable; }
        @Override public void run() { runnable.run(); }
    }

    private final Config config;

    private final AtomicBoolean started;
    private final AtomicBoolean stopping;
    private final AtomicBoolean closed;

    private final BlockingQueue<ALTask> queue;

    private final CountDownLatch startLatch;
    private final CountDownLatch stopLatch;

    private volatile Thread alThread;

    private volatile long deviceHandle;  // ALCdevice*
    private volatile long contextHandle; // ALCcontext*

    private volatile ALCCapabilities alcCaps;
    private volatile ALCapabilities alCaps;

    private final AtomicReference<Throwable> fatalError;

    public AuralisAL(Config config) {
        this.config = Objects.requireNonNull(config, "config");

        this.started = new AtomicBoolean(false);
        this.stopping = new AtomicBoolean(false);
        this.closed = new AtomicBoolean(false);

        this.queue = new LinkedBlockingQueue<>();

        this.startLatch = new CountDownLatch(1);
        this.stopLatch = new CountDownLatch(1);

        this.deviceHandle = 0L;
        this.contextHandle = 0L;

        this.alcCaps = null;
        this.alCaps = null;

        this.fatalError = new AtomicReference<>(null);
    }

    public void start() {
        ensureNotClosed();

        if (!started.compareAndSet(false, true)) {
            GFBsAuralis.LOGGER.debug("OpenAL already started, waiting for initialization");
            awaitStartOrThrow();
            return;
        }

        Thread t = new Thread(this::threadMain, config.threadName);
        t.setDaemon(config.daemonThread);
        this.alThread = t;
        GFBsAuralis.LOGGER.info("Launching OpenAL thread: {}", config.threadName);
        t.start();

        awaitStartOrThrow();
    }

    public void stop() {
        if (!started.get()) {
            GFBsAuralis.LOGGER.debug("OpenAL not started, skipping stop");
            return;
        }
        if (stopping.compareAndSet(false, true)) {
            GFBsAuralis.LOGGER.info("Stopping OpenAL thread: {}", config.threadName);
            queue.offer(() -> {
            });
        }

        try {
            stopLatch.await();
            GFBsAuralis.LOGGER.info("OpenAL thread stopped successfully: {}", config.threadName);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while stopping OpenAL thread", e);
        }

        Throwable fatal = fatalError.get();
        if (fatal != null) {
            throw new RuntimeException("OpenAL thread terminated with fatal error", fatal);
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            GFBsAuralis.LOGGER.info("Closing AuralisAL instance");
            stop();
        } else {
            GFBsAuralis.LOGGER.debug("AuralisAL already closed, skipping close");
        }
    }

    public boolean isStarted() {
        return started.get();
    }

    public boolean isStopping() {
        return stopping.get();
    }

    public boolean isClosed() {
        return closed.get();
    }

    public boolean isOnALThread() {
        Thread t = this.alThread;
        return t != null && Thread.currentThread() == t;
    }

    public void ensureRunning() {
        ensureNotClosed();
        if (!started.get()) {
            throw new IllegalStateException("AuralisAL not started");
        }
        Throwable fatal = fatalError.get();
        if (fatal != null) {
            GFBsAuralis.LOGGER.error("AuralisAL has fatal error; OpenAL thread is not healthy: {}", String.valueOf(fatal));
        }
    }

    public void submit(Runnable task) {
        Objects.requireNonNull(task, "task");
        ensureRunning();
        queue.offer(new TaskWrapper(task));
    }

    public <T> CompletableFuture<T> submit(Callable<T> task) {
        Objects.requireNonNull(task, "task");
        ensureRunning();

        CompletableFuture<T> future = new CompletableFuture<>();
        queue.offer(() -> {
            try {
                T v = task.call();
                future.complete(v);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    public void executeBlocking(Runnable task) {
        Objects.requireNonNull(task, "task");
        ensureRunning();

        if (isOnALThread()) {
            task.run();
            if (config.strictChecks) {
                alCheck("after executeBlocking on AL thread");
            }
            return;
        }

        CompletableFuture<Void> f = new CompletableFuture<>();
        queue.offer(() -> {
            try {
                task.run();
                if (config.strictChecks) {
                    alCheck("after executeBlocking task");
                }
                f.complete(null);
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        joinFuture(f);
    }

    public <T> T callBlocking(Callable<T> task) {
        Objects.requireNonNull(task, "task");
        ensureRunning();

        if (isOnALThread()) {
            try {
                T result = task.call();
                if (config.strictChecks) {
                    alCheck("after callBlocking on AL thread");
                }
                return result;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        CompletableFuture<T> f = submit(task);
        return joinFuture(f);
    }

    public long deviceHandle() {
        ensureRunning();
        return deviceHandle;
    }

    public long contextHandle() {
        ensureRunning();
        return contextHandle;
    }

    public ALCCapabilities alcCapabilities() {
        ensureRunning();
        return alcCaps;
    }

    public ALCapabilities alCapabilities() {
        ensureRunning();
        return alCaps;
    }

    private void threadMain() {
        GFBsAuralis.LOGGER.info("Starting OpenAL thread: {}", config.threadName);
        try {
            initOpenAL();
            GFBsAuralis.LOGGER.info("OpenAL initialized successfully on device: {}", config.deviceName != null ? config.deviceName : "default");
            startLatch.countDown();

            while (!stopping.get()) {
                ALTask task = queue.poll(config.idleWaitMillis, TimeUnit.MILLISECONDS);
                if (task != null) {
                    task.run();
                    if (config.strictChecks) {
                        alCheck("after task");
                    }
                }
            }
            GFBsAuralis.LOGGER.info("OpenAL thread stopping: {}", config.threadName);

        } catch (Throwable t) {
            fatalError.compareAndSet(null, t);
            GFBsAuralis.LOGGER.error("OpenAL thread encountered fatal error: {}", t.getMessage(), t);
            startLatch.countDown();
        } finally {
            try {
                destroyOpenAL();
                GFBsAuralis.LOGGER.info("OpenAL resources destroyed successfully");
            } catch (Throwable t2) {
                fatalError.compareAndSet(null, t2);
                GFBsAuralis.LOGGER.error("Error destroying OpenAL resources: {}", t2.getMessage(), t2);
            } finally {
                stopLatch.countDown();
            }
        }
    }

    private void initOpenAL() {
        long dev = alcOpenDevice(config.deviceName);
        if (dev == 0L) {
            throw new IllegalStateException("alcOpenDevice failed (deviceName=" + config.deviceName + ")");
        }
        this.deviceHandle = dev;

        this.alcCaps = ALC.createCapabilities(dev);

        long ctx;
        if (config.contextAttributes != null) {
            ctx = alcCreateContext(dev, config.contextAttributes);
        } else {
            ctx = alcCreateContext(dev, (int[]) null);
        }
        if (ctx == 0L) {
            alcCloseDevice(dev);
            this.deviceHandle = 0L;
            throw new IllegalStateException("alcCreateContext failed");
        }
        this.contextHandle = ctx;

        if (!alcMakeContextCurrent(ctx)) {
            alcDestroyContext(ctx);
            alcCloseDevice(dev);
            this.contextHandle = 0L;
            this.deviceHandle = 0L;
            throw new IllegalStateException("alcMakeContextCurrent failed");
        }

        this.alCaps = AL.createCapabilities(this.alcCaps);

        // 设置距离模型为AL_INVERSE_DISTANCE_CLAMPED，这样会考虑minDistance和maxDistance
        AL11.alDistanceModel(AL11.AL_INVERSE_DISTANCE_CLAMPED);

        if (config.strictChecks) {
            alcCheck("post-init");
            alCheck("post-init");
        }
    }

    private void destroyOpenAL() {
        alcMakeContextCurrent(0L);

        long ctx = this.contextHandle;
        if (ctx != 0L) {
            alcDestroyContext(ctx);
            this.contextHandle = 0L;
        }

        long dev = this.deviceHandle;
        if (dev != 0L) {
            alcCloseDevice(dev);
            this.deviceHandle = 0L;
        }

        this.alCaps = null;
        this.alcCaps = null;
    }

    public void alcCheck(String where) {
        long dev = this.deviceHandle;
        if (dev == 0L) {
            return;
        }
        int err = alcGetError(dev);
        if (err != ALC_NO_ERROR) {
            String msg = "ALC error at " + where + ": 0x" + Integer.toHexString(err);
            throw new IllegalStateException(msg);
        }
    }

    public void alCheck(String where) {
        // NOOOOOO!
        int err = alGetError();
        if (err != AL_NO_ERROR) {
            String errorMsg = getALErrorString(err);
//            GFBsAuralis.LOGGER.error("OpenAL error at {}: {} (0x{})", where, errorMsg,
//                    Integer.toHexString(err));

            if (err == AL_INVALID_VALUE) {
//                GFBsAuralis.LOGGER.warn("Invalid value detected, attempting to recover...");
                return;
            } else {
//                String msg = "AL error at " + where + ": 0x" + Integer.toHexString(err);
//                GFBsAuralis.LOGGER.error(msg);
            }
        }
    }

    private static String getALErrorString(int errorCode) {
        switch (errorCode) {
            case AL11.AL_NO_ERROR:
                return "AL_NO_ERROR (没有错误)";
            case AL11.AL_INVALID_NAME:
                return "AL_INVALID_NAME (无效的名称)";
            case AL11.AL_INVALID_ENUM:
                return "AL_INVALID_ENUM (无效的枚举值)";
            case AL11.AL_INVALID_VALUE:
                return "AL_INVALID_VALUE (无效的参数值)";
            case AL11.AL_INVALID_OPERATION:
                return "AL_INVALID_OPERATION (无效的操作)";
            case AL11.AL_OUT_OF_MEMORY:
                return "AL_OUT_OF_MEMORY (内存不足)";
            default:
                return "未知错误: 0x" + Integer.toHexString(errorCode);
        }
    }

    private void ensureNotClosed() {
        if (closed.get()) {
            throw new IllegalStateException("AuralisAL is closed");
        }
    }

    private void awaitStartOrThrow() {
        try {
            startLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while starting OpenAL thread", e);
        }

        Throwable fatal = fatalError.get();
        if (fatal != null) {
            throw new RuntimeException("OpenAL initialization failed", fatal);
        }

        if (deviceHandle == 0L || contextHandle == 0L || alcCaps == null || alCaps == null) {
            throw new IllegalStateException("OpenAL started but some handles/capabilities are missing");
        }
    }

    private static <T> T joinFuture(CompletableFuture<T> f) {
        try {
            return f.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for AL task", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            if (cause instanceof Error er) {
                throw er;
            }
            throw new RuntimeException("AL task failed", cause);
        }
    }

    public static AuralisAL createAndStartGlobal(Config config) {
        Objects.requireNonNull(config, "config");

        AuralisAL created = new AuralisAL(config);
        if (!GLOBAL.compareAndSet(null, created)) {
            // someone else installed one first
            GFBsAuralis.LOGGER.debug("Global AuralisAL instance already exists, returning existing instance");
            return GLOBAL.get();
        }

        GFBsAuralis.LOGGER.info("Creating and starting global AuralisAL instance");
        created.start();
        return created;
    }

    public static void stopAndClearGlobal() {
        AuralisAL inst = GLOBAL.getAndSet(null);
        if (inst != null) {
            GFBsAuralis.LOGGER.info("Stopping and clearing global AuralisAL instance");
            inst.close();
        } else {
            GFBsAuralis.LOGGER.debug("No global AuralisAL instance to stop, skipping");
        }
    }
}

package net.flamgop.borked.physics;

import com.github.stephengold.joltjni.*;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.readonly.ConstBodyLockInterfaceLocking;
import electrostatic4j.snaploader.LibraryInfo;
import electrostatic4j.snaploader.LoadingCriterion;
import electrostatic4j.snaploader.NativeBinaryLoader;
import electrostatic4j.snaploader.filesystem.DirectoryPath;
import electrostatic4j.snaploader.platform.NativeDynamicLibrary;
import electrostatic4j.snaploader.platform.util.PlatformPredicate;

public class PhysicsContext {
    private static PhysicsContext INSTANCE;

    private final TempAllocator tempAllocator;

    private final JobSystem jobSystem;
    private final PhysicsSystem physicsSystem;
    private final BodyInterface bodyInterface;
    private final ConstBodyLockInterfaceLocking bodyLockInterface;

    public PhysicsContext() {
        if (INSTANCE != null) throw new RuntimeException("PhysicsContext is a singleton and cannot be instantiated twice!");
        INSTANCE = this;

        LibraryInfo info = new LibraryInfo(null, "joltjni", DirectoryPath.USER_DIR);
        NativeBinaryLoader loader = new NativeBinaryLoader(info);

        NativeDynamicLibrary[] libraries = {
                new NativeDynamicLibrary("linux/aarch64/com/github/stephengold", PlatformPredicate.LINUX_ARM_64),
                new NativeDynamicLibrary("linux/armhf/com/github/stephengold", PlatformPredicate.LINUX_ARM_32),
                new NativeDynamicLibrary("linux/x86-64/com/github/stephengold", PlatformPredicate.LINUX_X86_64),
                new NativeDynamicLibrary("osx/aarch64/com/github/stephengold", PlatformPredicate.MACOS_ARM_64),
                new NativeDynamicLibrary("osx/x86-64/com/github/stephengold", PlatformPredicate.MACOS_X86_64),
                new NativeDynamicLibrary("windows/x86-64/com/github/stephengold", PlatformPredicate.WIN_X86_64)
        };
        loader.registerNativeLibraries(libraries).initPlatformLibrary();
        try {
            loader.loadLibrary(LoadingCriterion.CLEAN_EXTRACTION);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        JoltPhysicsObject.startCleaner();
        Jolt.registerDefaultAllocator();
        Jolt.installDefaultAssertCallback();
        Jolt.installDefaultTraceCallback();
        if (!Jolt.newFactory()) throw new RuntimeException("Couldn't create jolt factory");
        Jolt.registerTypes();

        this.tempAllocator = new TempAllocatorMalloc();

        int numWorkerThreads = Math.max(Math.min(Runtime.getRuntime().availableProcessors() / 2, 4), 1);
        this.jobSystem = new JobSystemThreadPool(Jolt.cMaxPhysicsJobs, Jolt.cMaxPhysicsBarriers, numWorkerThreads);

        final int numBroadPhaseLayers = 1;

        ObjectLayerPairFilterTable ovoFilter = new ObjectLayerPairFilterTable(Layers.NUM_LAYERS);
        ovoFilter.enableCollision(Layers.MOVING, Layers.MOVING);
        ovoFilter.enableCollision(Layers.MOVING, Layers.NON_MOVING);
        ovoFilter.enableCollision(Layers.PLAYER, Layers.MOVING);
        ovoFilter.enableCollision(Layers.PLAYER, Layers.NON_MOVING);
        ovoFilter.disableCollision(Layers.NON_MOVING, Layers.NON_MOVING);
        ovoFilter.disableCollision(Layers.GHOST, Layers.MOVING);
        ovoFilter.disableCollision(Layers.GHOST, Layers.NON_MOVING);
        ovoFilter.disableCollision(Layers.GHOST, Layers.PLAYER);

        BroadPhaseLayerInterfaceTable ovbLayerMap = new BroadPhaseLayerInterfaceTable(Layers.NUM_LAYERS, numBroadPhaseLayers);
        ovbLayerMap.mapObjectToBroadPhaseLayer(Layers.MOVING, 0);
        ovbLayerMap.mapObjectToBroadPhaseLayer(Layers.NON_MOVING, 0);
        ovbLayerMap.mapObjectToBroadPhaseLayer(Layers.PLAYER, 0);
        ovbLayerMap.mapObjectToBroadPhaseLayer(Layers.GHOST, 0);

        ObjectVsBroadPhaseLayerFilterTable ovbFilter = new ObjectVsBroadPhaseLayerFilterTable(ovbLayerMap, numBroadPhaseLayers, ovoFilter, Layers.NUM_LAYERS);

        this.physicsSystem = new PhysicsSystem();

        int maxBodies = 2048;
        int numBodyMutexes = 0; // "use default"
        int maxBodyPairs = 65536;
        int maxContacts = 20480;
        this.physicsSystem.init(maxBodies, numBodyMutexes, maxBodyPairs, maxContacts, ovbLayerMap, ovbFilter, ovoFilter);

        this.bodyInterface = physicsSystem.getBodyInterface();
        this.bodyLockInterface = physicsSystem.getBodyLockInterface();
    }

    public TempAllocator tempAllocator() {
        return tempAllocator;
    }

    public BodyInterface bodyInterface() {
        return bodyInterface;
    }

    public ConstBodyLockInterfaceLocking bodyLockInterface() {
        return bodyLockInterface;
    }

    public PhysicsSystem system() {
        return physicsSystem;
    }

    public Body addBody(BodyCreationSettings bcs, EActivation activation) {
        Body obj = bodyInterface.createBody(bcs);
        bodyInterface.addBody(obj, activation);
        physicsSystem.optimizeBroadPhase();
        return obj;
    }

    public void update(float timeStep, int numCollisionSteps) {
        physicsSystem.update(timeStep, numCollisionSteps, tempAllocator, jobSystem);
    }
}

package net.flamgop.borked.engine.memory;

import static org.lwjgl.util.vma.Vma.*;

public enum MemoryUsage {
    UNKNOWN(VMA_MEMORY_USAGE_UNKNOWN),
    GPU_ONLY(VMA_MEMORY_USAGE_GPU_ONLY),
    CPU_ONLY(VMA_MEMORY_USAGE_CPU_ONLY),
    CPU_TO_GPU(VMA_MEMORY_USAGE_CPU_TO_GPU),
    GPU_TO_CPU(VMA_MEMORY_USAGE_GPU_TO_CPU),
    CPU_COPY(VMA_MEMORY_USAGE_CPU_COPY),
    GPU_LAZILY_ALLOCATED(VMA_MEMORY_USAGE_GPU_LAZILY_ALLOCATED),
    AUTO(VMA_MEMORY_USAGE_AUTO),
    AUTO_PREFER_DEVICE(VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE),
    AUTO_PREFER_HOST(VMA_MEMORY_USAGE_AUTO_PREFER_HOST),
    ;
    final int vmaQualifier;
    MemoryUsage(int vmaQualifier) {
        this.vmaQualifier = vmaQualifier;
    }
    public int qualifier() {
        return vmaQualifier;
    }
}

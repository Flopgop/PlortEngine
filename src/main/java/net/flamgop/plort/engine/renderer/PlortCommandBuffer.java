package net.flamgop.plort.engine.renderer;

import net.flamgop.plort.engine.renderer.image.PlortFilter;
import net.flamgop.plort.engine.renderer.image.PlortImage;
import net.flamgop.plort.engine.renderer.memory.PlortBuffer;
import net.flamgop.plort.engine.renderer.memory.IndexType;
import net.flamgop.plort.engine.renderer.pipeline.PipelineBindPoint;
import net.flamgop.plort.engine.renderer.pipeline.PlortPipeline;
import net.flamgop.plort.engine.renderer.pipeline.PlortPipelineLayout;
import net.flamgop.plort.engine.renderer.pipeline.barrier.PlortBufferMemoryBarrier;
import net.flamgop.plort.engine.renderer.pipeline.barrier.PlortImageMemoryBarrier;
import net.flamgop.plort.engine.renderer.pipeline.barrier.PlortMemoryBarrier;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.Arrays;

import static org.lwjgl.vulkan.EXTMeshShader.*;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK14.vkCmdPushDescriptorSet;

public class PlortCommandBuffer implements AutoCloseable {
    private final VkCommandBuffer handle;

    private boolean begun = false;

    public PlortCommandBuffer(VkCommandBuffer handle) {
        this.handle = handle;
    }

    public PlortCommandBuffer(VkCommandBuffer handle, boolean alreadyBegun) {
        this.handle = handle;
        begun = alreadyBegun;
    }

    public void begin(VkCommandBufferBeginInfo pBeginInfo) {
        if (begun) throw new IllegalStateException("A buffer which has already begun cannot begin again.");
        vkBeginCommandBuffer(handle, pBeginInfo);
        begun = true;
    }

    private void checkBegun() {
        if (!begun) throw new IllegalStateException("Can't record commands to a command buffer that has not begun yet. Either call begin first, or if this buffer has already begun use the constructor to specify.");
    }

    public void bindPipeline(PipelineBindPoint bindPoint, PlortPipeline pipeline) {
        checkBegun();
        vkCmdBindPipeline(handle, bindPoint.qualifier(), pipeline.handle());
    }

    public void bindDescriptorSets(PipelineBindPoint bindPoint, PlortPipelineLayout layout, int firstSet, LongBuffer pDescriptorSets, IntBuffer pDynamicOffsets) {
        checkBegun();
        vkCmdBindDescriptorSets(handle, bindPoint.qualifier(), layout.handle(), firstSet, pDescriptorSets, pDynamicOffsets);
    }

    public void clearColorImage(PlortImage image, PlortImage.Layout imageLayout, VkClearColorValue pColor, VkImageSubresourceRange.Buffer pRanges) {
        checkBegun();
        vkCmdClearColorImage(handle, image.handle(), imageLayout.qualifier(), pColor, pRanges);
    }

    public void dispatch(int groupCountX, int groupCountY, int groupCountZ) {
        checkBegun();
        vkCmdDispatch(handle, groupCountX, groupCountY, groupCountZ);
    }

    public void dispatchIndirect(PlortBuffer buffer, long offset) {
        checkBegun();
        vkCmdDispatchIndirect(handle, buffer.handle(), offset);
    }

    // TODO: vkCmdSetEvent
    // TODO: vkCmdResetEvent
    // TODO: vkCmdWaitEvents

    public void pushConstants(PlortPipelineLayout layout, int stageFlags, int offset, ByteBuffer values) { // note: while lwjgl VK10 implements other buffers for this method, most push constants are complex enough to need to be made of multiple values and thus should be ByteBuffers. All other buffers can be reinterpreted as ByteBuffers.
        checkBegun();
        vkCmdPushConstants(handle, layout.handle(), stageFlags, offset, values);
    }

    public void pushConstants(PlortPipelineLayout layout, int stageFlags, int offset, MemorySegment pValues) {
        checkBegun();
        nvkCmdPushConstants(handle, layout.handle(), stageFlags, offset, (int) pValues.byteSize(), pValues.address());
    }

    public void pushDescriptorSet(PipelineBindPoint bindPoint, PlortPipelineLayout layout, int set, VkWriteDescriptorSet.Buffer writes) {
        checkBegun();
        vkCmdPushDescriptorSet(handle, bindPoint.qualifier(), layout.handle(), set, writes);
    }

    public void setViewport(int firstViewport, VkViewport.Buffer pViewports) {
        checkBegun();
        vkCmdSetViewport(handle, firstViewport, pViewports);
    }

    public void setScissor(int firstScissor, VkRect2D.Buffer pScissors) {
        checkBegun();
        vkCmdSetScissor(handle, firstScissor, pScissors);
    }

    public void setLineWidth(float lineWidth) {
        checkBegun();
        vkCmdSetLineWidth(handle, lineWidth);
    }

    public void setDepthBias(float depthBiasConstantFactor, float depthBiasClamp, float depthBiasSlopeFactor) {
        checkBegun();
        vkCmdSetDepthBias(handle, depthBiasConstantFactor, depthBiasClamp, depthBiasSlopeFactor);
    }

    public void setBlendConstants(FloatBuffer blendConstants) {
        checkBegun();
        vkCmdSetBlendConstants(handle, blendConstants);
    }

    public void setDepthBounds(float minDepthBounds, float maxDepthBounds) {
        checkBegun();
        vkCmdSetDepthBounds(handle, minDepthBounds, maxDepthBounds);
    }

    public void setStencilCompareMask(int faceMask, int compareMask) {
        checkBegun();
        vkCmdSetStencilCompareMask(handle, faceMask, compareMask);
    }

    public void setStencilWriteMask(int faceMask, int writeMask) {
        checkBegun();
        vkCmdSetStencilWriteMask(handle, faceMask, writeMask);
    }

    public void setStencilReference(int faceMask, int reference) {
        checkBegun();
        vkCmdSetStencilReference(handle, faceMask, reference);
    }

    public void bindIndexBuffer(PlortBuffer buffer, long offset, IndexType indexType) {
        checkBegun();
        vkCmdBindIndexBuffer(handle, buffer.handle(), offset, indexType.qualifier());
    }

    public void bindVertexBuffers(int firstBinding, PlortBuffer[] buffers, long[] offsets) {
        checkBegun();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer pBuffers = stack.callocLong(buffers.length);
            pBuffers.put(Arrays.stream(buffers).mapToLong(PlortBuffer::handle).toArray()).flip();
            LongBuffer pOffsets = stack.callocLong(offsets.length);
            pOffsets.put(offsets).flip();
            bindVertexBuffers(firstBinding, pBuffers, pOffsets);
        }
    }

    public void bindVertexBuffers(int firstBinding, LongBuffer pBuffers, LongBuffer pOffsets) {
        checkBegun();
        vkCmdBindVertexBuffers(handle, firstBinding, pBuffers, pOffsets);
    }

    public void draw(int vertexCount, int instanceCount, int firstVertex, int firstInstance) {
        checkBegun();
        vkCmdDraw(handle, vertexCount, instanceCount, firstVertex, firstInstance);
    }

    public void drawIndexed(int indexCount, int instanceCount, int firstIndex, int vertexOffset, int firstInstance) {
        checkBegun();
        vkCmdDrawIndexed(handle, indexCount, instanceCount, firstIndex, vertexOffset, firstInstance);
    }

    public void drawIndirect(PlortBuffer buffer, long offset, int drawCount, int stride) {
        checkBegun();
        vkCmdDrawIndirect(handle, buffer.handle(), offset, drawCount, stride);
    }

    public void drawIndexedIndirect(PlortBuffer buffer, long offset, int drawCount, int stride) {
        checkBegun();
        vkCmdDrawIndexedIndirect(handle, buffer.handle(), offset, drawCount, stride);
    }

    public void blitImage(PlortImage srcImage, PlortImage.Layout srcImageLayout, PlortImage dstImage, PlortImage.Layout dstImageLayout, VkImageBlit.Buffer pRegions, PlortFilter filter) {
        checkBegun();
        vkCmdBlitImage(handle, srcImage.handle(), srcImageLayout.qualifier(), dstImage.handle(), dstImageLayout.qualifier(), pRegions, filter.qualifier());
    }

    public void clearDepthStencilImage(PlortImage image, PlortImage.Layout imageLayout, VkClearDepthStencilValue pDepthStencil, VkImageSubresourceRange.Buffer pRanges) {
        checkBegun();
        vkCmdClearDepthStencilImage(handle, image.handle(), imageLayout.qualifier(), pDepthStencil, pRanges);
    }

    public void clearClearAttachments(VkClearAttachment.Buffer pAttachments, VkClearRect.Buffer pRects) {
        checkBegun();
        vkCmdClearAttachments(handle, pAttachments, pRects);
    }

    public void resolveImage(PlortImage srcImage, PlortImage.Layout srcImageLayout, PlortImage dstImage, PlortImage.Layout dstImagelayout, VkImageResolve.Buffer pRegions) {
        checkBegun();
        vkCmdResolveImage(handle, srcImage.handle(), srcImageLayout.qualifier(), dstImage.handle(), dstImagelayout.qualifier(), pRegions);
    }

    public void beginRenderPass(VkRenderPassBeginInfo pRenderPassBegin, int contents) {
        checkBegun();
        vkCmdBeginRenderPass(handle, pRenderPassBegin, contents);
    }

    public void nextSubpass(int contents) {
        checkBegun();
        vkCmdNextSubpass(handle, contents);
    }

    public void endRenderPass() {
        checkBegun();
        vkCmdEndRenderPass(handle);
    }

    public void updateBuffer(PlortBuffer dstBuffer, long dstOffset, ByteBuffer data) {
        checkBegun();
        vkCmdUpdateBuffer(handle, dstBuffer.handle(), dstOffset, data);
    }

    public void copyBuffer(PlortBuffer src, PlortBuffer dst, VkBufferCopy.Buffer pRegions) {
        checkBegun();
        vkCmdCopyBuffer(handle, src.handle(), dst.handle(), pRegions);
    }

    public void copyImage(PlortImage srcImage, PlortImage.Layout srcImageLayout, PlortImage dstImage, PlortImage.Layout dstImageLayout, VkImageCopy.Buffer pRegions) {
        checkBegun();
        vkCmdCopyImage(handle, srcImage.handle(), srcImageLayout.qualifier(), dstImage.handle(), dstImageLayout.qualifier(), pRegions);
    }

    public void copyBufferToImage(PlortBuffer srcBuffer, PlortImage dstImage, PlortImage.Layout dstImageLayout, VkBufferImageCopy.Buffer pRegions) {
        checkBegun();
        vkCmdCopyBufferToImage(handle, srcBuffer.handle(), dstImage.handle(), dstImageLayout.qualifier(), pRegions);
    }

    public void copyImageToBuffer(PlortImage srcImage, PlortImage.Layout srcImageLayout, PlortBuffer dstBuffer, VkBufferImageCopy.Buffer pRegions) {
        checkBegun();
        vkCmdCopyImageToBuffer(handle, srcImage.handle(), srcImageLayout.qualifier(), dstBuffer.handle(), pRegions);
    }

    public void fillBuffer(PlortBuffer dstBuffer, long dstOffset, long size, int data) {
        checkBegun();
        vkCmdFillBuffer(handle, dstBuffer.handle(), dstOffset, size, data);
    }

    public void pipelineBarrier(int srcStageMask, int dstStageMask, int dependencyFlags, PlortMemoryBarrier @Nullable [] memoryBarriers, PlortBufferMemoryBarrier @Nullable [] bufferMemoryBarriers, PlortImageMemoryBarrier @Nullable [] imageMemoryBarriers) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            pipelineBarrier(stack, srcStageMask, dstStageMask, dependencyFlags, memoryBarriers, bufferMemoryBarriers, imageMemoryBarriers);
        }
    }

    public void pipelineBarrier(MemoryStack stack, int srcStageMask, int dstStageMask, int dependencyFlags, PlortMemoryBarrier @Nullable [] memoryBarriers, PlortBufferMemoryBarrier @Nullable [] bufferMemoryBarriers, PlortImageMemoryBarrier @Nullable [] imageMemoryBarriers) {
        checkBegun();
        final VkMemoryBarrier.Buffer pMemoryBarriers;
        final VkBufferMemoryBarrier.Buffer pBufferMemoryBarriers;
        final VkImageMemoryBarrier.Buffer pImageMemoryBarriers;
        if (memoryBarriers != null) {
            pMemoryBarriers = VkMemoryBarrier.calloc(memoryBarriers.length, stack);
            for (PlortMemoryBarrier barrier : memoryBarriers) barrier.get(pMemoryBarriers.get());
            pMemoryBarriers.flip();
        } else pMemoryBarriers = null;
        if (bufferMemoryBarriers != null) {
            pBufferMemoryBarriers = VkBufferMemoryBarrier.calloc(bufferMemoryBarriers.length, stack);
            for (PlortBufferMemoryBarrier barrier : bufferMemoryBarriers) barrier.get(pBufferMemoryBarriers.get());
            pBufferMemoryBarriers.flip();
        } else pBufferMemoryBarriers = null;
        if (imageMemoryBarriers != null) {
            pImageMemoryBarriers = VkImageMemoryBarrier.calloc(imageMemoryBarriers.length, stack);
            for (PlortImageMemoryBarrier barrier : imageMemoryBarriers) barrier.get(stack, pImageMemoryBarriers.get());
            pImageMemoryBarriers.flip();
        } else pImageMemoryBarriers = null;
        pipelineBarrier(srcStageMask, dstStageMask, dependencyFlags, pMemoryBarriers, pBufferMemoryBarriers, pImageMemoryBarriers);
    }

    @ApiStatus.Internal
    public void pipelineBarrier(int srcStageMask, int dstStageMask, int dependencyFlags, @Nullable VkMemoryBarrier.Buffer pMemoryBarriers, @Nullable VkBufferMemoryBarrier.Buffer pBufferMemoryBarriers, @Nullable VkImageMemoryBarrier.Buffer pImageMemoryBarriers) {
        vkCmdPipelineBarrier(handle, srcStageMask, dstStageMask, dependencyFlags, pMemoryBarriers, pBufferMemoryBarriers, pImageMemoryBarriers);
    }

    // TODO: vkCmdBeginQuery
    // TODO: vkCmdEndQuery
    // TODO: vkCmdResetQueryPool
    // TODO: vkCmdWriteTimestamp

    public void executeCommands(PointerBuffer pCommandBuffers) {
        checkBegun();
        vkCmdExecuteCommands(handle, pCommandBuffers);
    }

    public void drawMeshTasksEXT(int groupCountX, int groupCountY, int groupCountZ) {
        checkBegun();
        vkCmdDrawMeshTasksEXT(handle, groupCountX, groupCountY, groupCountZ);
    }

    public void drawMeshTasksIndirectEXT(PlortBuffer buffer, long offset, int drawCount, int stride) {
        checkBegun();
        vkCmdDrawMeshTasksIndirectEXT(handle, buffer.handle(), offset, drawCount, stride);
    }

    public void drawMeshTasksIndirectCountEXT(PlortBuffer buffer, long offset, PlortBuffer countBuffer, long countBufferOffset, int maxDrawCount, int stride) {
        checkBegun();
        vkCmdDrawMeshTasksIndirectCountEXT(handle, buffer.handle(), offset, countBuffer.handle(), countBufferOffset, maxDrawCount, stride);
    }

    @Override
    public void close() {
        if (!begun) throw new IllegalStateException("Can't end (close) a command buffer that hasn't begun.");
        vkEndCommandBuffer(handle);
    }
}

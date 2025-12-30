package net.flamgop.borked.renderer;

import net.flamgop.borked.renderer.image.PlortFilter;
import net.flamgop.borked.renderer.image.PlortImage;
import net.flamgop.borked.renderer.memory.PlortBuffer;
import net.flamgop.borked.renderer.model.IndexType;
import net.flamgop.borked.renderer.pipeline.PipelineBindPoint;
import net.flamgop.borked.renderer.pipeline.PlortPipeline;
import net.flamgop.borked.renderer.pipeline.PlortPipelineLayout;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.Arrays;

import static org.lwjgl.vulkan.EXTMeshShader.*;
import static org.lwjgl.vulkan.VK10.*;

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
        vkBeginCommandBuffer(handle, pBeginInfo);
        begun = true;
    }

    public void bindPipeline(PipelineBindPoint bindPoint, PlortPipeline pipeline) {
        if (!begun) throw new IllegalStateException("Can't record commands to a command buffer that has not began yet. Either call begin first, or if this buffer has already began use the constructor to specify.");
        vkCmdBindPipeline(handle, bindPoint.qualifier(), pipeline.handle());
    }

    public void bindDescriptorSets(PipelineBindPoint bindPoint, PlortPipelineLayout layout, int firstSet, LongBuffer pDescriptorSets, IntBuffer pDynamicOffsets) {
        if (!begun) throw new IllegalStateException("Can't record commands to a command buffer that has not began yet. Either call begin first, or if this buffer has already began use the constructor to specify.");
        vkCmdBindDescriptorSets(handle, bindPoint.qualifier(), layout.handle(), firstSet, pDescriptorSets, pDynamicOffsets);
    }

    public void clearColorImage(PlortImage image, PlortImage.Layout imageLayout, VkClearColorValue pColor, VkImageSubresourceRange.Buffer pRanges) {
        if (!begun) throw new IllegalStateException("Can't record commands to a command buffer that has not began yet. Either call begin first, or if this buffer has already began use the constructor to specify.");
        vkCmdClearColorImage(handle, image.handle(), imageLayout.qualifier(), pColor, pRanges);
    }

    public void dispatch(int groupCountX, int groupCountY, int groupCountZ) {
        if (!begun) throw new IllegalStateException("Can't record commands to a command buffer that has not began yet. Either call begin first, or if this buffer has already began use the constructor to specify.");
        vkCmdDispatch(handle, groupCountX, groupCountY, groupCountZ);
    }

    public void dispatchIndirect(PlortBuffer buffer, long offset) {
        if (!begun) throw new IllegalStateException("Can't record commands to a command buffer that has not began yet. Either call begin first, or if this buffer has already began use the constructor to specify.");
        vkCmdDispatchIndirect(handle, buffer.handle(), offset);
    }

    // TODO: vkCmdSetEvent
    // TODO: vkCmdResetEvent
    // TODO: vkCmdWaitEvents

    public void pushConstants(PlortPipelineLayout layout, int stageFlags, int offset, ByteBuffer values) { // note: while lwjgl VK10 implements other buffers for this method, most push constants are complex enough to need to be made of multiple values and thus should be ByteBuffers. All other buffers can be reinterpreted as ByteBuffers.
        if (!begun) throw new IllegalStateException("Can't record commands to a command buffer that has not began yet. Either call begin first, or if this buffer has already began use the constructor to specify.");
        vkCmdPushConstants(handle, layout.handle(), stageFlags, offset, values);
    }

    public void setViewport(int firstViewport, VkViewport.Buffer pViewports) {
        if (!begun) throw new IllegalStateException("Can't record commands to a command buffer that has not began yet. Either call begin first, or if this buffer has already began use the constructor to specify.");
        vkCmdSetViewport(handle, firstViewport, pViewports);
    }

    public void setScissor(int firstScissor, VkRect2D.Buffer pScissors) {
        if (!begun) throw new IllegalStateException("Can't record commands to a command buffer that has not began yet. Either call begin first, or if this buffer has already began use the constructor to specify.");
        vkCmdSetScissor(handle, firstScissor, pScissors);
    }

    public void setLineWidth(float lineWidth) {
        if (!begun) throw new IllegalStateException("Can't record commands to a command buffer that has not began yet. Either call begin first, or if this buffer has already began use the constructor to specify.");
        vkCmdSetLineWidth(handle, lineWidth);
    }

    public void setDepthBias(float depthBiasConstantFactor, float depthBiasClamp, float depthBiasSlopeFactor) {
        if (!begun) throw new IllegalStateException("Can't record commands to a command buffer that has not began yet. Either call begin first, or if this buffer has already began use the constructor to specify.");
        vkCmdSetDepthBias(handle, depthBiasConstantFactor, depthBiasClamp, depthBiasSlopeFactor);
    }

    public void setBlendConstants(FloatBuffer blendConstants) {
        if (!begun) throw new IllegalStateException("Can't record commands to a command buffer that has not began yet. Either call begin first, or if this buffer has already began use the constructor to specify.");
        vkCmdSetBlendConstants(handle, blendConstants);
    }

    public void setDepthBounds(float minDepthBounds, float maxDepthBounds) {
        if (!begun) throw new IllegalStateException("Can't record commands to a command buffer that has not began yet. Either call begin first, or if this buffer has already began use the constructor to specify.");
        vkCmdSetDepthBounds(handle, minDepthBounds, maxDepthBounds);
    }

    public void setStencilCompareMask(int faceMask, int compareMask) {
        if (!begun) throw new IllegalStateException("Can't record commands to a command buffer that has not began yet. Either call begin first, or if this buffer has already began use the constructor to specify.");
        vkCmdSetStencilCompareMask(handle, faceMask, compareMask);
    }

    public void setStencilWriteMask(int faceMask, int writeMask) {
        if (!begun) throw new IllegalStateException("Can't record commands to a command buffer that has not began yet. Either call begin first, or if this buffer has already began use the constructor to specify.");
        vkCmdSetStencilWriteMask(handle, faceMask, writeMask);
    }

    public void setStencilReference(int faceMask, int reference) {
        if (!begun) throw new IllegalStateException("Can't record commands to a command buffer that has not began yet. Either call begin first, or if this buffer has already began use the constructor to specify.");
        vkCmdSetStencilReference(handle, faceMask, reference);
    }

    public void bindIndexBuffer(PlortBuffer buffer, long offset, IndexType indexType) {
        if (!begun) throw new IllegalStateException("Can't record commands to a command buffer that has not began yet. Either call begin first, or if this buffer has already began use the constructor to specify.");
        vkCmdBindIndexBuffer(handle, buffer.handle(), offset, indexType.qualifier());
    }

    public void bindVertexBuffers(int firstBinding, PlortBuffer[] buffers, long[] offsets) {
        if (!begun) throw new IllegalStateException("Can't record commands to a command buffer that has not began yet. Either call begin first, or if this buffer has already began use the constructor to specify.");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer pBuffers = stack.callocLong(buffers.length);
            pBuffers.put(Arrays.stream(buffers).mapToLong(PlortBuffer::handle).toArray()).flip();
            LongBuffer pOffsets = stack.callocLong(offsets.length);
            pOffsets.put(offsets).flip();
            bindVertexBuffers(firstBinding, pBuffers, pOffsets);
        }
    }

    public void bindVertexBuffers(int firstBinding, LongBuffer pBuffers, LongBuffer pOffsets) {
        if (!begun) throw new IllegalStateException("Can't record commands to a command buffer that has not began yet. Either call begin first, or if this buffer has already began use the constructor to specify.");
        vkCmdBindVertexBuffers(handle, firstBinding, pBuffers, pOffsets);
    }

    public void draw(int vertexCount, int instanceCount, int firstVertex, int firstInstance) {
        if (!begun) throw new IllegalStateException("Can't record commands to a command buffer that has not began yet. Either call begin first, or if this buffer has already began use the constructor to specify.");
        vkCmdDraw(handle, vertexCount, instanceCount, firstVertex, firstInstance);
    }

    public void drawIndexed(int indexCount, int instanceCount, int firstIndex, int vertexOffset, int firstInstance) {
        if (!begun) throw new IllegalStateException("Can't record commands to a command buffer that has not began yet. Either call begin first, or if this buffer has already began use the constructor to specify.");
        vkCmdDrawIndexed(handle, indexCount, instanceCount, firstIndex, vertexOffset, firstInstance);
    }

    public void drawIndirect(PlortBuffer buffer, long offset, int drawCount, int stride) {
        if (!begun) throw new IllegalStateException("Can't record commands to a command buffer that has not began yet. Either call begin first, or if this buffer has already began use the constructor to specify.");
        vkCmdDrawIndirect(handle, buffer.handle(), offset, drawCount, stride);
    }

    public void drawIndexedIndirect(PlortBuffer buffer, long offset, int drawCount, int stride) {
        if (!begun) throw new IllegalStateException("Can't record commands to a command buffer that has not began yet. Either call begin first, or if this buffer has already began use the constructor to specify.");
        vkCmdDrawIndexedIndirect(handle, buffer.handle(), offset, drawCount, stride);
    }

    public void blitImage(PlortImage srcImage, PlortImage.Layout srcImageLayout, PlortImage dstImage, PlortImage.Layout dstImageLayout, VkImageBlit.Buffer pRegions, PlortFilter filter) {
        if (!begun) throw new IllegalStateException("Can't record commands to a command buffer that has not began yet. Either call begin first, or if this buffer has already began use the constructor to specify.");
        vkCmdBlitImage(handle, srcImage.handle(), srcImageLayout.qualifier(), dstImage.handle(), dstImageLayout.qualifier(), pRegions, filter.qualifier());
    }

    public void clearDepthStencilImage(PlortImage image, PlortImage.Layout imageLayout, VkClearDepthStencilValue pDepthStencil, VkImageSubresourceRange.Buffer pRanges) {
        if (!begun) throw new IllegalStateException("Can't record commands to a command buffer that has not began yet. Either call begin first, or if this buffer has already began use the constructor to specify.");
        vkCmdClearDepthStencilImage(handle, image.handle(), imageLayout.qualifier(), pDepthStencil, pRanges);
    }

    public void clearClearAttachments(VkClearAttachment.Buffer pAttachments, VkClearRect.Buffer pRects) {
        if (!begun) throw new IllegalStateException("Can't record commands to a command buffer that has not began yet. Either call begin first, or if this buffer has already began use the constructor to specify.");
        vkCmdClearAttachments(handle, pAttachments, pRects);
    }

    public void resolveImage(PlortImage srcImage, PlortImage.Layout srcImageLayout, PlortImage dstImage, PlortImage.Layout dstImagelayout, VkImageResolve.Buffer pRegions) {
        if (!begun) throw new IllegalStateException("Can't record commands to a command buffer that has not began yet. Either call begin first, or if this buffer has already began use the constructor to specify.");
        vkCmdResolveImage(handle, srcImage.handle(), srcImageLayout.qualifier(), dstImage.handle(), dstImagelayout.qualifier(), pRegions);
    }

    public void beginRenderPass(VkRenderPassBeginInfo pRenderPassBegin, int contents) {
        if (!begun) throw new IllegalStateException("Can't record commands to a command buffer that has not began yet. Either call begin first, or if this buffer has already began use the constructor to specify.");
        vkCmdBeginRenderPass(handle, pRenderPassBegin, contents);
    }

    public void nextSubpass(int contents) {
        if (!begun) throw new IllegalStateException("Can't record commands to a command buffer that has not began yet. Either call begin first, or if this buffer has already began use the constructor to specify.");
        vkCmdNextSubpass(handle, contents);
    }

    public void endRenderPass() {
        if (!begun) throw new IllegalStateException("Can't record commands to a command buffer that has not began yet. Either call begin first, or if this buffer has already began use the constructor to specify.");
        vkCmdEndRenderPass(handle);
    }

    public void updateBuffer(PlortBuffer dstBuffer, long dstOffset, ByteBuffer data) {
        if (!begun) throw new IllegalStateException("Can't record commands to a command buffer that has not began yet. Either call begin first, or if this buffer has already began use the constructor to specify.");
        vkCmdUpdateBuffer(handle, dstBuffer.handle(), dstOffset, data);
    }

    public void copyBuffer(PlortBuffer src, PlortBuffer dst, VkBufferCopy.Buffer pRegions) {
        if (!begun) throw new IllegalStateException("Can't record commands to a command buffer that has not began yet. Either call begin first, or if this buffer has already began use the constructor to specify.");
        vkCmdCopyBuffer(handle, src.handle(), dst.handle(), pRegions);
    }

    public void copyImage(PlortImage srcImage, PlortImage.Layout srcImageLayout, PlortImage dstImage, PlortImage.Layout dstImageLayout, VkImageCopy.Buffer pRegions) {
        if (!begun) throw new IllegalStateException("Can't record commands to a command buffer that has not began yet. Either call begin first, or if this buffer has already began use the constructor to specify.");
        vkCmdCopyImage(handle, srcImage.handle(), srcImageLayout.qualifier(), dstImage.handle(), dstImageLayout.qualifier(), pRegions);
    }

    public void copyBufferToImage(PlortBuffer srcBuffer, PlortImage dstImage, PlortImage.Layout dstImageLayout, VkBufferImageCopy.Buffer pRegions) {
        if (!begun) throw new IllegalStateException("Can't record commands to a command buffer that has not began yet. Either call begin first, or if this buffer has already began use the constructor to specify.");
        vkCmdCopyBufferToImage(handle, srcBuffer.handle(), dstImage.handle(), dstImageLayout.qualifier(), pRegions);
    }

    public void copyImageToBuffer(PlortImage srcImage, PlortImage.Layout srcImageLayout, PlortBuffer dstBuffer, VkBufferImageCopy.Buffer pRegions) {
        if (!begun) throw new IllegalStateException("Can't record commands to a command buffer that has not began yet. Either call begin first, or if this buffer has already began use the constructor to specify.");
        vkCmdCopyImageToBuffer(handle, srcImage.handle(), srcImageLayout.qualifier(), dstBuffer.handle(), pRegions);
    }

    public void fillBuffer(PlortBuffer dstBuffer, long dstOffset, long size, int data) {
        if (!begun) throw new IllegalStateException("Can't record commands to a command buffer that has not began yet. Either call begin first, or if this buffer has already began use the constructor to specify.");
        vkCmdFillBuffer(handle, dstBuffer.handle(), dstOffset, size, data);
    }

    public void pipelineBarrier(int srcStageMask, int dstStageMask, int dependencyFlags, VkMemoryBarrier.Buffer pMemoryBarriers, VkBufferMemoryBarrier.Buffer pBufferMemoryBarriers, VkImageMemoryBarrier.Buffer pImageMemoryBarriers) {
        if (!begun) throw new IllegalStateException("Can't record commands to a command buffer that has not began yet. Either call begin first, or if this buffer has already began use the constructor to specify.");
        vkCmdPipelineBarrier(handle, srcStageMask, dstStageMask, dependencyFlags, pMemoryBarriers, pBufferMemoryBarriers, pImageMemoryBarriers);
    }

    // TODO: vkCmdBeginQuery
    // TODO: vkCmdEndQuery
    // TODO: vkCmdResetQueryPool
    // TODO: vkCmdWriteTimestamp

    public void executeCommands(PointerBuffer pCommandBuffers) {
        if (!begun) throw new IllegalStateException("Can't record commands to a command buffer that has not began yet. Either call begin first, or if this buffer has already began use the constructor to specify.");
        vkCmdExecuteCommands(handle, pCommandBuffers);
    }

    public void drawMeshTasksEXT(int groupCountX, int groupCountY, int groupCountZ) {
        if (!begun) throw new IllegalStateException("Can't record commands to a command buffer that has not began yet. Either call begin first, or if this buffer has already began use the constructor to specify.");
        vkCmdDrawMeshTasksEXT(handle, groupCountX, groupCountY, groupCountZ);
    }

    public void drawMeshTasksIndirectEXT(PlortBuffer buffer, long offset, int drawCount, int stride) {
        if (!begun) throw new IllegalStateException("Can't record commands to a command buffer that has not began yet. Either call begin first, or if this buffer has already began use the constructor to specify.");
        vkCmdDrawMeshTasksIndirectEXT(handle, buffer.handle(), offset, drawCount, stride);
    }

    public void drawMeshTasksIndirectCountEXT(PlortBuffer buffer, long offset, PlortBuffer countBuffer, long countBufferOffset, int maxDrawCount, int stride) {
        if (!begun) throw new IllegalStateException("Can't record commands to a command buffer that has not began yet. Either call begin first, or if this buffer has already began use the constructor to specify.");
        vkCmdDrawMeshTasksIndirectCountEXT(handle, buffer.handle(), offset, countBuffer.handle(), countBufferOffset, maxDrawCount, stride);
    }

    @Override
    public void close() {
        if (!begun) throw new IllegalStateException("Can't end (close) a command buffer that hasn't begun.");
        vkEndCommandBuffer(handle);
    }
}

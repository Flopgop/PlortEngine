package net.flamgop.plort.engine.renderer.renderpass;

import net.flamgop.plort.engine.renderer.image.ImageFormat;
import net.flamgop.plort.engine.renderer.image.PlortImage;

public record PlortAttachment(ImageFormat format, int sampleCount, AttachmentLoadOp loadOp, AttachmentStoreOp storeOp, AttachmentLoadOp stencilLoadOp, AttachmentStoreOp stencilStoreOp, PlortImage.Layout initialLayout, PlortImage.Layout finalLayout, RenderPassImageViewSupplier imageViewSupplier) {
}

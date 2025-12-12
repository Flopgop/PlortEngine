package net.flamgop.borked.renderer.renderpass;

import net.flamgop.borked.renderer.image.ImageFormat;
import net.flamgop.borked.renderer.image.PlortImage;

public record PlortAttachment(ImageFormat format, int sampleCount, AttachmentLoadOp loadOp, AttachmentStoreOp storeOp, AttachmentLoadOp stencilLoadOp, AttachmentStoreOp stencilStoreOp, PlortImage.Layout initialLayout, PlortImage.Layout finalLayout, RenderPassImageViewSupplier imageViewSupplier) {
}

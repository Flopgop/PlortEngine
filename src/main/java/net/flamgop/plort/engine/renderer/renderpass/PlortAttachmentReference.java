package net.flamgop.plort.engine.renderer.renderpass;

import net.flamgop.plort.engine.renderer.image.PlortImage;

public record PlortAttachmentReference(int attachment, PlortImage.Layout layout) {
}

package net.flamgop.borked.renderer.renderpass;

import net.flamgop.borked.renderer.image.PlortImage;

public record PlortAttachmentReference(int attachment, PlortImage.Layout layout) {
}

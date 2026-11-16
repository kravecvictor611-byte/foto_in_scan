package com.vladimir.photopdf;

import android.net.Uri;

final class PhotoPage {
    final Uri uri;
    // Crop coordinates in the decoded, orientation-correct image, 0..1.
    float left = 0f, top = 0f, right = 1f, bottom = 1f;
    int rotation = 0;
    int filter = 0; // 0: color, 1: gray, 2: high contrast

    PhotoPage(Uri uri) { this.uri = uri; }
}

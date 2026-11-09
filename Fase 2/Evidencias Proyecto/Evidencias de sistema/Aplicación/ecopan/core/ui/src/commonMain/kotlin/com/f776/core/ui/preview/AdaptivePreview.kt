package com.f776.core.ui.preview

import androidx.compose.ui.tooling.preview.Preview

@Preview(name = "phone", device = "spec:width=411dp,height=891dp")
@Preview(name = "foldable",device = "spec:width=673dp,height=841dp")
@Preview(name = "tablet",device = "spec:width=1280dp,height=800dp,dpi=240")
@Preview(name = "desktop",device = "spec:width=1920dp,height=1080dp,dpi=160")

annotation class AdaptivePreview

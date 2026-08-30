

package com.cglabs.lifemusic.models

import com.music.innertube.models.YTItem
import com.cglabs.lifemusic.db.entities.LocalItem

data class SimilarRecommendation(
    val title: LocalItem,
    val items: List<YTItem>,
)

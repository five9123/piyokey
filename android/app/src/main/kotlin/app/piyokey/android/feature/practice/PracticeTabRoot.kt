package app.piyokey.android.feature.practice

import androidx.compose.runtime.Composable

/** Practice tab root: the curriculum map (iOS `CurriculumMapView`, tab "Practice"). */
@Composable
fun PracticeTabRoot() {
  CurriculumMapScreen(isHatchOnboarding = false)
}

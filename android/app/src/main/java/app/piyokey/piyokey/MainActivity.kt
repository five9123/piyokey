package app.piyokey.piyokey

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      PiyokeyTheme {
        ReadinessScreen()
      }
    }
  }
}

@Composable
private fun PiyokeyTheme(content: @Composable () -> Unit) {
  MaterialTheme(content = content)
}

@Composable
private fun ReadinessScreen() {
  Scaffold { insets ->
    Surface(
      modifier = Modifier
        .fillMaxSize()
        .padding(insets),
    ) {
      Column(
        modifier = Modifier.padding(PaddingValues(horizontal = 24.dp, vertical = 32.dp)),
        verticalArrangement = Arrangement.spacedBy(20.dp),
      ) {
        Text(
          text = stringResource(R.string.app_name),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary,
        )
        Text(
          text = stringResource(R.string.readiness_title),
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = stringResource(R.string.readiness_body),
          style = MaterialTheme.typography.bodyLarge,
        )
        ReadinessCard(
          label = stringResource(R.string.contract_label),
          value = stringResource(R.string.contract_value),
        )
        ReadinessCard(
          label = stringResource(R.string.next_step_label),
          value = stringResource(R.string.next_step_value),
        )
      }
    }
  }
}

@Composable
private fun ReadinessCard(label: String, value: String) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.secondaryContainer,
    ),
  ) {
    Column(
      modifier = Modifier.padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Text(text = label, style = MaterialTheme.typography.labelMedium)
      Text(
        text = value,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )
    }
  }
}

@Preview(showBackground = true)
@Composable
private fun ReadinessScreenPreview() {
  PiyokeyTheme {
    ReadinessScreen()
  }
}

package staff.android.adoption

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable private fun Brand() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.ic_common_goods), null, Modifier.size(36.dp))
        Spacer(Modifier.width(10.dp))
        Column { Text("Common Goods", style = MaterialTheme.typography.titleLarge)
            Text("EVERYDAY, WELL CHOSEN", style = MaterialTheme.typography.labelSmall) }
    }
}
@Composable private fun TeaPhoto(modifier: Modifier) {
    BoxWithConstraints(modifier) {
        // Bound the measured image height directly. An impossible aspect ratio can
        // otherwise choose a required size and draw beyond a lazy item's bounds.
        val photoHeight = (maxWidth / 1.5f).coerceAtMost(330.dp)
        Image(painterResource(R.drawable.product_tea), "Tea in a blue tin",
            Modifier.fillMaxWidth().height(photoHeight).clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant), contentScale = ContentScale.Fit)
    }
}
@Composable fun ShopApp(graph: ShopGraph) {
    val state by graph.state.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf("Shop") }
    BackHandler(screen != "Shop") { screen = "Shop" }
    CommonTheme {
        Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 760.dp).fillMaxWidth().semantics { testTagsAsResourceId = true }) {
                    Brand()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (tab in listOf("Shop", "Saved", "Cart")) {
                            FilterChip(selected = tab == screen, onClick = { screen = tab },
                                label = { Text(tab) }, modifier = Modifier.weight(1f).testTag("nav_${tab.lowercase()}"),
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary))
                        }
                    }
                    if (!state.loaded && state.busy) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.testTag("loading")) }
                    else LazyColumn(Modifier.fillMaxSize().testTag("shopping_content"), contentPadding = PaddingValues(20.dp, 18.dp, 20.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        item { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(when (screen) { "Shop" -> "Small rituals,\ngood things."; "Saved" -> "Your good finds."; else -> "Your cart." }, style = MaterialTheme.typography.displaySmall)
                            Text(when (screen) { "Shop" -> "Thoughtful essentials for the everyday."; "Saved" -> "Keep the things you love close. Add a little everyday ritual to your cart."; else -> "A few things to make the everyday better." }, style = MaterialTheme.typography.bodyLarge)
                        } }
                        if (screen == "Cart" && state.loaded && state.quantity == 0) {
                            item { Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text("A little room for something good.", Modifier.testTag("empty_cart"), style = MaterialTheme.typography.headlineMedium)
                                Text("Your saved Tea is ready whenever you are.")
                                Button(onClick = { screen = "Saved" }, Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("browse_saved")) { Text("Explore your saved items") }
                            } }
                        } else {
                            item { TeaPhoto(Modifier.fillMaxWidth().testTag("tea_photo")) }
                            item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("THE EVERYDAY PANTRY", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text("Tea", Modifier.testTag("product_name"), style = MaterialTheme.typography.headlineMedium)
                                Text("A little everyday ritual.", style = MaterialTheme.typography.bodyLarge)
                                Text("₹499", Modifier.testTag("product_price"), style = MaterialTheme.typography.titleLarge)
                                if (screen == "Shop") Text("A good find, already in your saved collection.")
                            } }
                            if (screen == "Shop") item { Button(onClick = { screen = "Saved" }, Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("open_saved")) { Text("Explore your saved items") } }
                            if (screen != "Shop" && state.loaded) {
                                item { Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(20.dp)) {
                                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("In your cart: ${state.quantity}", Modifier.testTag("cart_quantity"), style = MaterialTheme.typography.titleLarge)
                                        Text("Up to 4 Tea per cart.", style = MaterialTheme.typography.bodyMedium)
                                        if (screen == "Cart") Text("Subtotal ₹${state.quantity * 499}", Modifier.testTag("cart_subtotal"), style = MaterialTheme.typography.titleMedium)
                                    }
                                } }
                                item { Button(onClick = { if (screen == "Saved") graph.addSaved() else graph.addCart() }, enabled = !state.busy && !state.error,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("add_tea")) { Text(if (state.busy) "Saving…" else "Add 1 Tea") } }
                                if (screen == "Cart") item { OutlinedButton(onClick = { graph.setQuantity((state.quantity - 1).coerceAtLeast(0)) }, enabled = !state.busy && !state.error,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("remove_tea")) { Text("Remove 1 Tea") } }
                            }
                        }
                        state.message?.let { message -> item { Surface(color = if (state.error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(18.dp)) {
                            Text(message, Modifier.fillMaxWidth().padding(18.dp).testTag("cart_message").semantics { liveRegion = LiveRegionMode.Polite }, color = if (state.error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer)
                        } } }
                        if (state.error) item { OutlinedButton(onClick = graph::refresh, Modifier.fillMaxWidth().testTag("retry_cart")) { Text("Try again") } }
                        if (screen == "Saved") item { TextButton(onClick = { screen = "Cart" }, Modifier.fillMaxWidth().testTag("view_cart")) { Text("View your cart") } }
                    }
                }
            }
        }
    }
}

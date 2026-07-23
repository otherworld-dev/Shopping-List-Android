package dev.otherworld.shoppinglist.ui.items

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.otherworld.shoppinglist.domain.model.ItemModel
import dev.otherworld.shoppinglist.domain.model.ShopAreaModel
import dev.otherworld.shoppinglist.ui.theme.DefaultBrand
import dev.otherworld.shoppinglist.ui.theme.ShoppingListTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Renders the list rows with invented data and writes PNGs to the app's internal files dir, so
 * the density of the shop-area presentation can be eyeballed without a Nextcloud account.
 *
 * Run it so the app survives the run (`connectedDebugAndroidTest` uninstalls afterwards, taking
 * the PNGs with it), then read them out:
 *
 *   adb install -r app/build/outputs/apk/debug/app-debug.apk
 *   adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
 *   adb shell am instrument -w -e class dev.otherworld.shoppinglist.ui.items.ItemsListScreenshotTest \
 *       dev.otherworld.shoppinglist.test/androidx.test.runner.AndroidJUnitRunner
 *   adb exec-out run-as dev.otherworld.shoppinglist cat files/items-before-after.png > out.png
 */
@RunWith(AndroidJUnit4::class)
class ItemsListScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val dairy = ShopAreaModel(1, 1, "Dairy", 0, "#3B82F6", emptyList())
    private val produce = ShopAreaModel(2, 1, "Fruit & Veg", 1, "#22C55E", emptyList())

    private fun item(id: Long, name: String, area: ShopAreaModel?, qty: String? = null, checked: Boolean = false) =
        ItemModel(
            id = id,
            listId = 1,
            name = name,
            quantity = qty,
            unit = null,
            shopAreaId = area?.id,
            checked = checked,
            checkedBy = null,
            sortOrder = id.toInt(),
        )

    @Test
    fun capturesBeforeAndAfter() {
        composeRule.setContent {
            ShoppingListTheme(brandColor = DefaultBrand) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    // verticalScroll gives the unbounded height constraint the rows see inside
                    // the real LazyColumn — without it the headers' fillMaxHeight stripe expands
                    // to the whole viewport.
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        Caption("BEFORE — header bar + name on every row")
                        LegacySectionHeader("Dairy", Color(0xFF3B82F6), 3)
                        GroupRows(showAreaName = true)
                        LegacySectionHeader("Fruit & Veg", Color(0xFF22C55E), 2)
                        ProduceRows(showAreaName = true)

                        Spacer(Modifier.heightIn(min = 24.dp).fillMaxWidth())

                        Caption("AFTER — stripe + dot only")
                        SectionHeader("Dairy", Color(0xFF3B82F6), 3)
                        GroupRows(showAreaName = false)
                        SectionHeader("Fruit & Veg", Color(0xFF22C55E), 2)
                        ProduceRows(showAreaName = false)

                    }
                }
            }
        }
        composeRule.onRoot().captureToImage().save("items-before-after.png")
    }

    /** Comfy (today, ~68dp rows) vs compact (~46dp) so the density trade-off can be eyeballed. */
    @Test
    fun capturesDensity() {
        val rows = listOf("Cheese" to "2", "Yoghurt" to null, "Milk" to "1", "Cream" to null)
        composeRule.setContent {
            ShoppingListTheme(brandColor = DefaultBrand) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        Caption("COMFY — today, 48dp tap target")
                        SectionHeader("Dairy", Color(0xFF3B82F6), rows.size)
                        rows.forEachIndexed { i, (n, q) ->
                            ItemRow(item(i + 1L, n, dairy, q), dairy, false, true, i % 2 == 1, {}, {}, compact = false)
                        }

                        Spacer(Modifier.heightIn(min = 28.dp).fillMaxWidth())

                        Caption("COMPACT — 36dp tap target, tighter rows")
                        SectionHeader("Dairy", Color(0xFF3B82F6), rows.size)
                        rows.forEachIndexed { i, (n, q) ->
                            ItemRow(item(i + 1L, n, dairy, q), dairy, false, true, i % 2 == 1, {}, {}, compact = true)
                        }
                    }
                }
            }
        }
        composeRule.onRoot().captureToImage().save("density.png")
    }

    /** Checked items are listed together across areas, so the name has to survive there. */
    @Test
    fun capturesCheckedSection() {
        composeRule.setContent {
            ShoppingListTheme(brandColor = DefaultBrand) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        SectionHeader("Checked", null, 2)
                        ItemRow(
                            item = item(9, "Butter", dairy, checked = true),
                            area = dairy,
                            showAreaName = true,
                            enabled = true,
                            alt = false,
                            onToggle = {},
                            onClick = {},
                        )
                        ItemRow(
                            item = item(10, "Apples", produce, checked = true),
                            area = produce,
                            showAreaName = true,
                            enabled = true,
                            alt = true,
                            onToggle = {},
                            onClick = {},
                        )
                    }
                }
            }
        }
        composeRule.onRoot().captureToImage().save("checked-section.png")
    }

    /**
     * Scrolls a real LazyColumn past the "Dairy" header and checks the pinned copy keeps it
     * on screen — the case that justifies dropping the per-row name.
     */
    @Test
    fun capturesPinnedHeader() {
        // Long enough to overflow the viewport — otherwise the list cannot scroll and the
        // header never leaves the screen, which would prove nothing.
        val dairyItems = listOf(
            "Cheese", "Yoghurt", "Milk", "Cream", "Butter", "Skyr", "Kefir", "Quark",
            "Mozzarella", "Feta", "Brie", "Halloumi", "Ricotta", "Mascarpone", "Creme fraiche",
            "Sour cream", "Buttermilk", "Cottage cheese", "Parmesan", "Gouda",
        )
        val rows: List<Row> = buildList {
            add(Row.AreaHeaderRow(dairy, dairyItems.size))
            dairyItems.forEachIndexed { i, n ->
                add(Row.ItemRowData(item(i + 1L, n, dairy), draggable = true, alt = i % 2 == 1))
            }
            add(Row.AreaHeaderRow(produce, 2))
            listOf("Tomatoes", "Spinach").forEachIndexed { i, n ->
                add(Row.ItemRowData(item(i + 20L, n, produce), draggable = true, alt = i % 2 == 1))
            }
        }
        composeRule.setContent {
            ShoppingListTheme(brandColor = DefaultBrand) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    // Start scrolled into the middle of Dairy, so its real header is off-screen.
                    val listState = rememberLazyListState(initialFirstVisibleItemIndex = 8)
                    // fillMaxSize, as in the real screen: a wrap-height Box would give the
                    // LazyColumn unbounded height, so it would lay out every row and never scroll.
                    Box(Modifier.fillMaxSize()) {
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                            items(rows, key = { it.key }) { row ->
                                when (row) {
                                    is Row.AreaHeaderRow ->
                                        SectionHeader(row.area?.name ?: "Other", Color(0xFF3B82F6), row.count)
                                    is Row.ItemRowData ->
                                        ItemRow(
                                            item = row.item,
                                            area = if (row.item.shopAreaId == dairy.id) dairy else produce,
                                            showAreaName = false,
                                            enabled = true,
                                            alt = row.alt,
                                            onToggle = {},
                                            onClick = {},
                                        )
                                    is Row.CheckedHeaderRow -> Unit
                                }
                            }
                        }
                        PinnedSectionHeader(rows, listState)
                    }
                }
            }
        }
        composeRule.onRoot().captureToImage().save("pinned-header.png")
    }

    @Composable
    private fun GroupRows(showAreaName: Boolean) {
        listOf("Cheese" to "2", "Yoghurt" to null, "Milk" to "1")
            .forEachIndexed { i, (name, qty) ->
                ItemRow(
                    item = item(i + 1L, name, dairy, qty),
                    area = dairy,
                    showAreaName = showAreaName,
                    enabled = true,
                    alt = i % 2 == 1,
                    onToggle = {},
                    onClick = {},
                )
            }
    }

    @Composable
    private fun ProduceRows(showAreaName: Boolean) {
        listOf("Tomatoes", "Spinach").forEachIndexed { i, name ->
            ItemRow(
                item = item(i + 5L, name, produce),
                area = produce,
                showAreaName = showAreaName,
                enabled = true,
                alt = i % 2 == 1,
                onToggle = {},
                onClick = {},
            )
        }
    }

    @Composable
    private fun Caption(text: String) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }

    /** Verbatim copy of the pre-change SectionHeader, for an honest side-by-side. */
    @Composable
    private fun LegacySectionHeader(name: String, color: Color?, count: Int) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .heightIn(min = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box4dp(color ?: MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text(
                name.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }

    @Composable
    private fun Box4dp(color: Color) {
        Spacer(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .heightIn(min = 40.dp)
                .background(color),
        )
    }

    private fun ImageBitmap.save(name: String) {
        // Internal storage: shell can't read /sdcard/Android/data on Android 11+, but
        // `adb exec-as run-as <pkg> cat files/<name>` works for a debuggable build.
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
        File(dir, name).outputStream().use { out ->
            asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }
}

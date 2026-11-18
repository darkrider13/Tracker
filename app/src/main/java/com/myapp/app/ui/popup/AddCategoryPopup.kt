package com.myapp.app.ui.popup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.myapp.app.data.AppStore
import com.myapp.app.data.CustomCategory
import com.myapp.app.ui.components.AppText
import com.myapp.app.ui.components.FaIcon
import com.myapp.app.ui.components.PagedViewport
import com.myapp.app.ui.components.pagedItem
import com.myapp.app.ui.components.rememberPagedState
import com.myapp.app.ui.theme.AppTheme
import com.myapp.app.ui.theme.Brand
import com.myapp.app.util.CUSTOM_CATEGORY_ICONS
import com.myapp.app.util.QUICK_CATEGORIES
import com.myapp.app.util.cleanCategoryName
import com.myapp.app.util.getCustomCategories
import com.myapp.app.util.resolveCanonicalCategory
import com.myapp.app.util.syncCustomCategories
import com.myapp.app.util.tr

/** The "New Category" popup (old openAddCategoryModal / saveCustomCategory / deleteCustomCategory). */
object AddCategory {
    var name by mutableStateOf(TextFieldValue(""))
    var icon by mutableStateOf(CUSTOM_CATEGORY_ICONS[0])
    var error by mutableStateOf<String?>(null)
    private var onCreated: (String) -> Unit = {}

    /** [created] is called with the new category's name (it gets selected in the popup that opened this one). */
    fun open(created: (String) -> Unit) {
        onCreated = created
        icon = CUSTOM_CATEGORY_ICONS[0]
        name = TextFieldValue("")
        error = null
        Popups.addCategory = true
    }

    fun cancel() {
        Popups.addCategory = false
    }

    fun save() {
        val clean = cleanCategoryName(name.text)
        if (clean.isEmpty()) {
            error = tr("cat_err_name", "Enter a category name.")
            return
        }
        val reserved = listOf("add money", "debt collection", "debt repayment", "debt lent", "debt borrowed", "fund transfer")
        val hit = resolveCanonicalCategory(clean)
        if ((hit != null && (QUICK_CATEGORIES.contains(hit) || hit == "Other")) || reserved.contains(clean.lowercase())) {
            error = tr("cat_err_exists", "A category with this name already exists.")
            return
        }
        val state = AppStore.state
        state.customCategories.add(CustomCategory(name = clean, icon = icon))
        state.categoryOrder = state.categoryOrder.filter { it != clean }.toMutableList()
        state.categoryOrder.add(0, clean)
        AppStore.saveState()
        syncCustomCategories()
        Popups.addCategory = false
        onCreated(clean)
    }

    fun delete(categoryName: String) {
        AppDialogs.confirm(
            message = tr("cat_delete_confirm", "Delete this category? Past transactions stay; only the icon changes."),
            icon = "fa-trash-alt",
        ) { ok ->
            if (ok == true) {
                val state = AppStore.state
                state.customCategories = getCustomCategories().filter { it.name != categoryName }.toMutableList()
                state.categoryOrder = state.categoryOrder.filter { it != categoryName }.toMutableList()
                AppStore.saveState()
                syncCustomCategories()
                error = null
                AppStore.refresh()
            }
        }
    }
}

@Composable
fun AddCategoryPopup() {
    val c = AppTheme.colors
    PopupLayer(visible = Popups.addCategory, scrim = 0.6f) {
        GlassCard(radius = 24.dp, padding = 20.dp) {
            val state = AppStore.observe()
            val mine = state.customCategories
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppText(
                    text = tr("cat_new_title", "New Category"),
                    size = 18f,
                    lineHeight = 28f,
                    weight = FontWeight.ExtraBold,
                    letterSpacingEm = -0.025f,
                    color = c.textGray900,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(Brand.red, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { FaIcon(AddCategory.icon, 14.dp, Color.White) }
                    Box(modifier = Modifier.weight(1f)) {
                        AppTextField(
                            value = AddCategory.name,
                            onValueChange = { AddCategory.name = it },
                            placeholder = tr("cat_new_name_placeholder", "Category name"),
                            maxLength = 20,
                        )
                    }
                }

                val err = AddCategory.error
                if (err != null) {
                    AppText(
                        text = err,
                        size = 10f,
                        weight = FontWeight.Bold,
                        color = Brand.red,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }

                AppText(
                    text = tr("cat_choose_icon", "Choose Icon"),
                    size = 10f,
                    weight = FontWeight.Bold,
                    letterSpacingEm = 0.1f,
                    color = c.textGray400,
                    uppercase = true,
                )

                // icon picker: 6 per row, 4 rows visible, scrolls one row at a time
                val rows = CUSTOM_CATEGORY_ICONS.chunked(6)
                val gridPaged = rememberPagedState(visible = 4, gap = 8.dp)
                PagedViewport(state = gridPaged, count = rows.size, modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for ((ri, rowIcons) in rows.withIndex()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().pagedItem(gridPaged, ri),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                for (ic in rowIcons) {
                                    val on = ic == AddCategory.icon
                                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(if (on) Brand.red else c.bgGray200a70, CircleShape)
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null,
                                                ) { AddCategory.icon = ic },
                                            contentAlignment = Alignment.Center,
                                        ) { FaIcon(ic, 12.dp, if (on) Color.White else c.textGray500) }
                                    }
                                }
                                // keep the last (short) row's cells the same width as full rows
                                repeat(6 - rowIcons.size) { Box(modifier = Modifier.weight(1f)) }
                            }
                        }
                    }
                }

                if (mine.isNotEmpty()) {
                    AppText(
                        text = tr("cat_my_categories", "My Categories"),
                        size = 10f,
                        weight = FontWeight.Bold,
                        letterSpacingEm = 0.1f,
                        color = c.textGray400,
                        uppercase = true,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    val minePaged = rememberPagedState(visible = 2, gap = 6.dp)
                    PagedViewport(state = minePaged, count = mine.size, modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            for ((i, cat) in mine.withIndex()) {
                                val rowShape = RoundedCornerShape(12.dp)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .pagedItem(minePaged, i)
                                        .clip(rowShape)
                                        .background(c.bgWhite70, rowShape)
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        FaIcon(cat.icon, 12.dp, Brand.red)
                                        AppText(
                                            text = cat.name,
                                            size = 12f,
                                            lineHeight = 16f,
                                            weight = FontWeight.Bold,
                                            color = c.textGray800,
                                            singleLineEllipsis = true,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                            ) { AddCategory.delete(cat.name) },
                                        contentAlignment = Alignment.Center,
                                    ) { FaIcon("fa-trash-alt", 10.dp, c.textGray400) }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ModalButton(tr("common_cancel", "Cancel"), ModalButtonKind.Secondary, Modifier.weight(1f)) { AddCategory.cancel() }
                    ModalButton(tr("common_save", "Save"), ModalButtonKind.Green, Modifier.weight(1f)) { AddCategory.save() }
                }
            }
        }
    }
}

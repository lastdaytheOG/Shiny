

package com.shiny.music.ui.screens.settings

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.shiny.music.ui.liquid.LiquidSwitch as Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import com.shiny.music.ui.liquid.LiquidTopAppBar as TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.shiny.music.LocalPlayerAwareWindowInsets
import com.shiny.music.R
import com.shiny.music.constants.LyricsRomanizeAsMainKey
import com.shiny.music.constants.LyricsRomanizeBelarusianKey
import com.shiny.music.constants.LyricsRomanizeBulgarianKey
import com.shiny.music.constants.LyricsRomanizeChineseKey
import com.shiny.music.constants.LyricsRomanizeHindiKey
import com.shiny.music.constants.LyricsRomanizePunjabiKey
import com.shiny.music.constants.LyricsRomanizeCyrillicByLineKey
import com.shiny.music.constants.LyricsRomanizeJapaneseKey
import com.shiny.music.constants.LyricsRomanizeKoreanKey
import com.shiny.music.constants.LyricsRomanizeKyrgyzKey
import com.shiny.music.constants.LyricsRomanizeMacedonianKey
import com.shiny.music.constants.LyricsRomanizeRussianKey
import com.shiny.music.constants.LyricsRomanizeSerbianKey
import com.shiny.music.constants.LyricsRomanizeUkrainianKey
import com.shiny.music.ui.component.ActionPromptDialog
import com.shiny.music.ui.component.IconButton
import com.shiny.music.ui.component.Material3SettingsGroup
import com.shiny.music.ui.component.Material3SettingsItem
import com.shiny.music.ui.utils.backToMain
import com.shiny.music.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RomanizationSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val scrollState = androidx.compose.foundation.rememberScrollState()

    val context = LocalContext.current

    val (lyricsRomanizeAsMain, onLyricsRomanizeAsMainChange) = rememberPreference(LyricsRomanizeAsMainKey, defaultValue = false)
    val (lyricsRomanizeJapanese, onLyricsRomanizeJapaneseChange) = rememberPreference(LyricsRomanizeJapaneseKey, defaultValue = true)
    val (lyricsRomanizeKorean, onLyricsRomanizeKoreanChange) = rememberPreference(LyricsRomanizeKoreanKey, defaultValue = true)
    val (lyricsRomanizeChinese, onLyricsRomanizeChineseChange) = rememberPreference(LyricsRomanizeChineseKey, defaultValue = true)
    val (lyricsRomanizeHindi, onLyricsRomanizeHindiChange) = rememberPreference(LyricsRomanizeHindiKey, defaultValue = true)
    val (lyricsRomanizePunjabi, onLyricsRomanizePunjabiChange) = rememberPreference(LyricsRomanizePunjabiKey, defaultValue = true)
    val (lyricsRomanizeRussian, onLyricsRomanizeRussianChange) = rememberPreference(LyricsRomanizeRussianKey, defaultValue = true)
    val (lyricsRomanizeUkrainian, onLyricsRomanizeUkrainianChange) = rememberPreference(LyricsRomanizeUkrainianKey, defaultValue = true)
    val (lyricsRomanizeSerbian, onLyricsRomanizeSerbianChange) = rememberPreference(LyricsRomanizeSerbianKey, defaultValue = true)
    val (lyricsRomanizeBulgarian, onLyricsRomanizeBulgarianChange) = rememberPreference(LyricsRomanizeBulgarianKey, defaultValue = true)
    val (lyricsRomanizeBelarusian, onLyricsRomanizeBelarusianChange) = rememberPreference(LyricsRomanizeBelarusianKey, defaultValue = true)
    val (lyricsRomanizeKyrgyz, onLyricsRomanizeKyrgyzChange) = rememberPreference(LyricsRomanizeKyrgyzKey, defaultValue = true)
    val (lyricsRomanizeMacedonian, onLyricsRomanizeMacedonianChange) = rememberPreference(LyricsRomanizeMacedonianKey, defaultValue = true)
    val (lyricsRomanizeCyrillicByLine, onLyricsRomanizeCyrillicByLineChange) = rememberPreference(LyricsRomanizeCyrillicByLineKey, defaultValue = false)
    val (showDialog, setShowDialog) = remember { mutableStateOf(false) }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal))
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)))

        Spacer(modifier = Modifier.height(16.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.general),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.lyrics),
                    title = { Text(stringResource(R.string.lyrics_romanize_as_main)) },
                    description = { Text(stringResource(R.string.lyrics_romanize_as_main_desc)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsRomanizeAsMain,
                            onCheckedChange = onLyricsRomanizeAsMainChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (lyricsRomanizeAsMain) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onLyricsRomanizeAsMainChange(!lyricsRomanizeAsMain) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.language_japanese_latin),
                    title = { Text(stringResource(R.string.lyrics_romanize_japanese)) },
                    description = { Text(stringResource(R.string.lyrics_romanize_japanese_desc)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsRomanizeJapanese,
                            onCheckedChange = onLyricsRomanizeJapaneseChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (lyricsRomanizeJapanese) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onLyricsRomanizeJapaneseChange(!lyricsRomanizeJapanese) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.language_korean_latin),
                    title = { Text(stringResource(R.string.lyrics_romanize_korean)) },
                    description = { Text(stringResource(R.string.lyrics_romanize_korean_desc)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsRomanizeKorean,
                            onCheckedChange = onLyricsRomanizeKoreanChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (lyricsRomanizeKorean) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onLyricsRomanizeKoreanChange(!lyricsRomanizeKorean) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.language),
                    title = { Text(stringResource(R.string.lyrics_romanize_chinese)) },
                    description = { Text(stringResource(R.string.lyrics_romanize_chinese_desc)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsRomanizeChinese,
                            onCheckedChange = onLyricsRomanizeChineseChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (lyricsRomanizeChinese) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onLyricsRomanizeChineseChange(!lyricsRomanizeChinese) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.language),
                    title = { Text(stringResource(R.string.lyrics_romanize_hindi)) },
                    description = { Text(stringResource(R.string.lyrics_romanize_hindi_desc)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsRomanizeHindi,
                            onCheckedChange = onLyricsRomanizeHindiChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (lyricsRomanizeHindi) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onLyricsRomanizeHindiChange(!lyricsRomanizeHindi) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.language),
                    title = { Text(stringResource(R.string.lyrics_romanize_punjabi)) },
                    description = { Text(stringResource(R.string.lyrics_romanize_punjabi_desc)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsRomanizePunjabi,
                            onCheckedChange = onLyricsRomanizePunjabiChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (lyricsRomanizePunjabi) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onLyricsRomanizePunjabiChange(!lyricsRomanizePunjabi) }
                )
            )
        )

        

        Spacer(modifier = Modifier.height(16.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.lyrics_romanization_cyrillic),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.alphabet_cyrillic),
                    title = { Text(stringResource(R.string.lyrics_romanize_russian)) },
                    description = { Text(stringResource(R.string.lyrics_romanize_russian_desc)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsRomanizeRussian,
                            onCheckedChange = onLyricsRomanizeRussianChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (lyricsRomanizeRussian) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onLyricsRomanizeRussianChange(!lyricsRomanizeRussian) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.alphabet_cyrillic),
                    title = { Text(stringResource(R.string.lyrics_romanize_ukrainian)) },
                    description = { Text(stringResource(R.string.lyrics_romanize_ukrainian_desc)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsRomanizeUkrainian,
                            onCheckedChange = onLyricsRomanizeUkrainianChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (lyricsRomanizeUkrainian) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onLyricsRomanizeUkrainianChange(!lyricsRomanizeUkrainian) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.alphabet_cyrillic),
                    title = { Text(stringResource(R.string.lyrics_romanize_serbian)) },
                    description = { Text(stringResource(R.string.lyrics_romanize_serbian_desc)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsRomanizeSerbian,
                            onCheckedChange = onLyricsRomanizeSerbianChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (lyricsRomanizeSerbian) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onLyricsRomanizeSerbianChange(!lyricsRomanizeSerbian) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.alphabet_cyrillic),
                    title = { Text(stringResource(R.string.lyrics_romanize_bulgarian)) },
                    description = { Text(stringResource(R.string.lyrics_romanize_bulgarian_desc)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsRomanizeBulgarian,
                            onCheckedChange = onLyricsRomanizeBulgarianChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (lyricsRomanizeBulgarian) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onLyricsRomanizeBulgarianChange(!lyricsRomanizeBulgarian) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.alphabet_cyrillic),
                    title = { Text(stringResource(R.string.lyrics_romanize_belarusian)) },
                    description = { Text(stringResource(R.string.lyrics_romanize_belarusian_desc)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsRomanizeBelarusian,
                            onCheckedChange = onLyricsRomanizeBelarusianChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (lyricsRomanizeBelarusian) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onLyricsRomanizeBelarusianChange(!lyricsRomanizeBelarusian) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.alphabet_cyrillic),
                    title = { Text(stringResource(R.string.lyrics_romanize_kyrgyz)) },
                    description = { Text(stringResource(R.string.lyrics_romanize_kyrgyz_desc)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsRomanizeKyrgyz,
                            onCheckedChange = onLyricsRomanizeKyrgyzChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (lyricsRomanizeKyrgyz) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onLyricsRomanizeKyrgyzChange(!lyricsRomanizeKyrgyz) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.alphabet_cyrillic),
                    title = { Text(stringResource(R.string.lyrics_romanize_macedonian)) },
                    description = { Text(stringResource(R.string.lyrics_romanize_macedonian_desc)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsRomanizeMacedonian,
                            onCheckedChange = onLyricsRomanizeMacedonianChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (lyricsRomanizeMacedonian) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onLyricsRomanizeMacedonianChange(!lyricsRomanizeMacedonian) }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.warning),
                    title = { Text(stringResource(R.string.line_by_line_option_title)) },
                    description = { Text(stringResource(R.string.line_by_line_option_desc)) },
                    trailingContent = {
                        Switch(
                            checked = lyricsRomanizeCyrillicByLine,
                            onCheckedChange = {
                                if (it) {
                                    setShowDialog(true)
                                } else {
                                    onLyricsRomanizeCyrillicByLineChange(false)
                                }
                            },
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (lyricsRomanizeCyrillicByLine) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = {
                        if (!lyricsRomanizeCyrillicByLine) {
                            setShowDialog(true)
                        } else {
                            onLyricsRomanizeCyrillicByLineChange(false)
                        }
                    }
                )
            )
        )
        if (showDialog) {
            ActionPromptDialog(
                title = stringResource(R.string.line_by_line_dialog_title),
                onDismiss = { setShowDialog(false) },
                onConfirm = {
                    onLyricsRomanizeCyrillicByLineChange(true)
                    setShowDialog(false)
                },
                onCancel = { setShowDialog(false) },
                content = {
                    Text(stringResource(R.string.line_by_line_dialog_desc))
                }
            )
        }
    
        Spacer(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.lyrics_romanize_title)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        }
    )
}

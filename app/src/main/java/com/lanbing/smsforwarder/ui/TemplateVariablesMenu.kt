package com.lanbing.smsforwarder.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/** A disclosure within the editor: no navigation, preference writes or draft reset. */
@Composable
internal fun TemplateVariablesMenu(
    variables: List<Pair<String,String>>,
    onInsert: (String) -> Unit,
    tagPrefix: String = "template-variables",
    stateKey: String = tagPrefix
) {
    var expanded by rememberSaveable(stateKey) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.55f))
            .clickable(role=Role.Button) { expanded = !expanded }
            .heightIn(min=48.dp).padding(horizontal=12.dp,vertical=10.dp)
            .semantics { stateDescription=if (expanded) "已展开" else "已收起" }
            .testTag("$tagPrefix-toggle"),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("可用变量（全部 ${variables.size} 项）",style=MaterialTheme.typography.titleSmall)
                Text(if (expanded) "点击收起" else "点击展开",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,contentDescription=null,modifier=Modifier.size(24.dp))
        }
        AnimatedVisibility(expanded,
            enter=expandVertically(tween(180))+fadeIn(tween(120)),
            exit=shrinkVertically(tween(160))+fadeOut(tween(100))) {
            Column(Modifier.fillMaxWidth().padding(start=12.dp,top=8.dp).testTag("$tagPrefix-list"),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text("点击变量插入到光标处",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                variables.forEach { (token,description) ->
                    val tag=if (tagPrefix=="template-variables") "template-variable-${token.trim('{','}')}" else "$tagPrefix-item-${token.trim('{','}')}"
                    OutlinedButton(onClick={ onInsert(token) },shape=RoundedCornerShape(12.dp),
                        modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag(tag),contentPadding=PaddingValues(horizontal=12.dp,vertical=8.dp)) {
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                            Text(description,style=MaterialTheme.typography.bodySmall)
                            Text(token,style=MaterialTheme.typography.bodyMedium,fontFamily=FontFamily.Monospace)
                        }
                        Icon(Icons.Outlined.Add,contentDescription=null,modifier=Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

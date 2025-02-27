package com.sanmer.mrepo.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sanmer.mrepo.R

@Composable
fun ModuleCard(
    name: String,
    version: String,
    author: String,
    description: String,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
    decoration: TextDecoration = TextDecoration.None,
    switch: @Composable (() -> Unit?)? = null,
    indicator: @Composable (BoxScope.() -> Unit?)? = null,
    leadingButton: @Composable (RowScope.() -> Unit)? = null,
    trailingButton: @Composable RowScope.() -> Unit,
)  = Surface(
    modifier = modifier,
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 1.dp,
    shape = RoundedCornerShape(20.dp)
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(all = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .alpha(alpha = alpha)
                        .weight(1f)
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleSmall
                            .copy(fontWeight = FontWeight.Bold),
                        maxLines = 2,
                        textDecoration = decoration,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(id = R.string.module_version_author,
                            version, author),
                        style = MaterialTheme.typography.bodySmall,
                        textDecoration = decoration
                    )
                }

                switch?.invoke()
            }

            Text(
                modifier = Modifier
                    .alpha(alpha = alpha)
                    .padding(horizontal = 16.dp),
                text = description,
                style = MaterialTheme.typography.bodySmall,
                textDecoration = decoration
            )

            Divider(
                thickness = 1.5.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Row(
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                leadingButton?.invoke(this)
                Spacer(modifier = Modifier.weight(1f))
                trailingButton()
            }
        }

        indicator?.invoke(this)
    }
}

@Composable
fun stateIndicator(
    @DrawableRes id: Int,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
): @Composable BoxScope.() -> Unit = {
    Image(
        modifier = Modifier.fillMaxSize(),
        painter = painterResource(id = id),
        contentDescription = null,
        alpha = 0.05f,
        colorFilter = ColorFilter.tint(color)
    )
}

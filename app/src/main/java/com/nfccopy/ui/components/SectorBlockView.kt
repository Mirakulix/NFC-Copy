package com.nfccopy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nfccopy.ui.theme.HexBackground
import com.nfccopy.ui.theme.HexText
import com.nfccopy.util.ByteUtils

@Composable
fun SectorBlockView(
    sectorData: Map<Int, List<String>>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        sectorData.toSortedMap().forEach { (sector, blocks) ->
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Sektor $sector",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(HexBackground)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    blocks.forEachIndexed { index, blockHex ->
                        val displayText = if (blockHex == "AUTH_FAILED" || blockHex == "ERROR") {
                            "Block $index: [$blockHex]"
                        } else {
                            val formatted = blockHex.chunked(2).joinToString(" ")
                            val ascii = try {
                                ByteUtils.bytesToAscii(ByteUtils.hexToBytes(blockHex))
                            } catch (_: Exception) { "" }
                            "Block $index: $formatted  |$ascii|"
                        }

                        Text(
                            text = displayText,
                            color = HexText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        )
                    }
                }
            }
        }
    }
}

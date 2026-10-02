package com.example.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.CycleItemCard
import com.example.ui.theme.CyanPrimary
import com.example.ui.viewmodel.HistoryFilter
import com.example.ui.viewmodel.WashViewModel

@Composable
fun HistoryScreen(
    viewModel: WashViewModel,
    modifier: Modifier = Modifier
) {
    val cycles by viewModel.filteredCycles.collectAsState()
    val activeFilter by viewModel.historyFilter.collectAsState()
    val selectedCycleForDetail by viewModel.selectedCycleForDetail.collectAsState()
    val selectedMachine by viewModel.selectedMachine.collectAsState()

    // Detail sheet if a cycle is clicked
    selectedCycleForDetail?.let { cycle ->
        CycleDetailModal(
            cycle = cycle,
            onDismiss = { viewModel.closeCycleDetail() }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Screen Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "HISTORICAL CYCLES",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "Washing Cycles Log",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(CyanPrimary.copy(alpha = 0.12f))
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = CyanPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Filter Chips Row: All | Today | Last 7 days | Last 30 days
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = activeFilter == HistoryFilter.ALL,
                    onClick = { viewModel.setHistoryFilter(HistoryFilter.ALL) },
                    label = { Text("All Cycles") },
                    shape = RoundedCornerShape(12.dp)
                )
            }
            item {
                FilterChip(
                    selected = activeFilter == HistoryFilter.TODAY,
                    onClick = { viewModel.setHistoryFilter(HistoryFilter.TODAY) },
                    label = { Text("Today") },
                    shape = RoundedCornerShape(12.dp)
                )
            }
            item {
                FilterChip(
                    selected = activeFilter == HistoryFilter.LAST_7_DAYS,
                    onClick = { viewModel.setHistoryFilter(HistoryFilter.LAST_7_DAYS) },
                    label = { Text("Last 7 Days") },
                    shape = RoundedCornerShape(12.dp)
                )
            }
            item {
                FilterChip(
                    selected = activeFilter == HistoryFilter.LAST_30_DAYS,
                    onClick = { viewModel.setHistoryFilter(HistoryFilter.LAST_30_DAYS) },
                    label = { Text("Last 30 Days") },
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // History Table / List
        if (cycles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.LocalLaundryService,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No washing cycles found",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "No telemetry cycles matched the selected date filter.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Table Header Legend
                item {
                    Text(
                        text = "Showing ${cycles.size} recorded runs for ${selectedMachine?.name ?: "WM-001"}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                items(cycles, key = { it.id }) { cycle ->
                    CycleItemCard(
                        cycle = cycle,
                        onClick = { viewModel.openCycleDetail(cycle) }
                    )
                }
            }
        }
    }
}

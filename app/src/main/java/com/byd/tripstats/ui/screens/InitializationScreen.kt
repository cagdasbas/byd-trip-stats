package com.byd.tripstats.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.byd.tripstats.data.config.CarCatalog
import com.byd.tripstats.data.config.CarConfig
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InitializationScreen(
    onContinue: suspend (car: CarConfig) -> Unit
) {
    var selectedCar by remember { mutableStateOf<CarConfig?>(null) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val canContinue = selectedCar != null

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Initialization screen",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(scrollState)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "1) Choose the BYD model you drive",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "This selection will be saved and used to load the correct car configuration.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Only DiLink 3 vehicles are supported. DiLink 4 and 5 are not yet supported.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(8.dp))

            CarCategorySection(
                categoryTitle = "Battery Electric (BEV)",
                groups = CarCatalog.groupedBev,
                selectedCarId = selectedCar?.id,
                onCarClick = { selectedCar = it }
            )

            CarCategorySection(
                categoryTitle = "Plug-in Hybrid (PHEV / DM-i)",
                groups = CarCatalog.groupedPhev,
                selectedCarId = selectedCar?.id,
                onCarClick = { selectedCar = it }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    val car = selectedCar ?: return@Button
                    scope.launch {
                        onContinue(car)
                    }
                },
                enabled = canContinue,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Continue")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CarCategorySection(
    categoryTitle: String,
    groups: Map<String, List<CarConfig>>,
    selectedCarId: String?,
    onCarClick: (CarConfig) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = categoryTitle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        HorizontalDivider()

        groups.forEach { (groupTitle, cars) ->
            CarGroupSection(
                title = groupTitle,
                cars = cars,
                selectedCarId = selectedCarId,
                onCarClick = onCarClick
            )
        }
    }
}

@Composable
private fun CarGroupSection(
    title: String,
    cars: List<CarConfig>,
    selectedCarId: String?,
    onCarClick: (CarConfig) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        cars.forEach { car ->
            CarOptionCard(
                car = car,
                selected = selectedCarId == car.id,
                onClick = { onCarClick(car) }
            )
        }
    }
}

@Composable
private fun CarOptionCard(
    car: CarConfig,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.DirectionsCar,
                contentDescription = null
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = car.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                val subtitle = if (car.isPhev) {
                    "EV range: ${car.wltpKm} km | ${car.phevUsableBatteryKwh ?: car.batteryKwh} kWh | ${car.drivetrain}"
                } else {
                    "WLTP: ${car.wltpKm} km | ${car.batteryKwh} kWh | ${car.drivetrain}"
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            RadioButton(
                selected = selected,
                onClick = onClick
            )
        }
    }
}

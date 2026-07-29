package com.example.airpodsbattery

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BluetoothBatteryApp()
                }
            }
        }
    }
}

data class DeviceBatteryInfo(
    val name: String,
    val address: String,
    val isLikelyAirPods: Boolean,
    val batteryLevel: Int?,
    val bondStateLabel: String
)

@Composable
fun BluetoothBatteryApp() {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf(listOf<DeviceBatteryInfo>()) }
    var statusMessage by remember { mutableStateOf("Vérification des permissions...") }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPermission = result.values.all { it }
        statusMessage = if (hasPermission) {
            "Permissions accordées"
        } else {
            "Permissions refusées. L'application ne peut pas lire les appareils Bluetooth."
        }
    }

    LaunchedEffect(Unit) {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }

        hasPermission = requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        if (!hasPermission) {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    fun refreshDevices() {
        devices = getBondedDevicesBatteryInfo(context)
        statusMessage = if (devices.isEmpty()) {
            "Aucun appareil Bluetooth appairé trouvé ou aucune donnée accessible."
        } else {
            "${devices.size} appareil(s) trouvé(s)"
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            refreshDevices()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "AirPods Battery",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = statusMessage,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (hasPermission) {
                    refreshDevices()
                }
            },
            enabled = hasPermission
        ) {
            Text("Actualiser")
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(devices) { device ->
                DeviceCard(device)
            }
        }
    }
}

@Composable
fun DeviceCard(device: DeviceBatteryInfo) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = device.name,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Adresse: ${device.address}")
            Text(text = "AirPods probables: ${if (device.isLikelyAirPods) "Oui" else "Non"}")
            Text(text = "Appairage: ${device.bondStateLabel}")
            Text(
                text = "Batterie: ${device.batteryLevel?.let { "$it%" } ?: "Non disponible"}"
            )
        }
    }
}

fun getBondedDevicesBatteryInfo(context: android.content.Context): List<DeviceBatteryInfo> {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val permissionGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED

        if (!permissionGranted) return emptyList()
    }

    val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
    val bondedDevices = bluetoothAdapter.bondedDevices ?: emptySet()

    return bondedDevices.map { device ->
        DeviceBatteryInfo(
            name = device.name ?: "Appareil inconnu",
            address = device.address ?: "Adresse inconnue",
            isLikelyAirPods = isProbablyAirPods(device),
            batteryLevel = readBatteryLevel(device),
            bondStateLabel = bondStateToLabel(device.bondState)
        )
    }.sortedByDescending { it.isLikelyAirPods }
}

fun isProbablyAirPods(device: BluetoothDevice): Boolean {
    val name = device.name?.lowercase() ?: return false
    return name.contains("airpods") || name.contains("airpod")
}

fun bondStateToLabel(state: Int): String {
    return when (state) {
        BluetoothDevice.BOND_BONDED -> "Appairé"
        BluetoothDevice.BOND_BONDING -> "Appairage en cours"
        BluetoothDevice.BOND_NONE -> "Non appairé"
        else -> "Inconnu"
    }
}

fun readBatteryLevel(device: BluetoothDevice): Int? {
    return try {
        val method = device.javaClass.getMethod("getBatteryLevel")
        val result = method.invoke(device) as? Int
        if (result != null && result in 0..100) result else null
    } catch (e: Exception) {
        null
    }
}


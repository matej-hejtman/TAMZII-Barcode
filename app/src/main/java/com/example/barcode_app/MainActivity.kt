package com.example.barcode_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.barcode_app.ui.theme.Barcode_appTheme


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            Barcode_appTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BarcodeScreen(modifier = Modifier.padding(innerPadding).fillMaxSize().padding(24.dp))
                }
            }
        }
    }
}

// Vzory pro levou polovinu UPC-A.
private val Lp = intArrayOf(
    0x0D, // 0001101
    0x19, // 0011001
    0x13, // 0010011
    0x3D, // 0111101
    0x23, // 0100011
    0x31, // 0110001
    0x2F, // 0101111
    0x3B, // 0111011
    0x37, // 0110111
    0x0B  // 0001011
)

// Vzory pro pravou polovinu UPC-A.
private val Rp = intArrayOf(
    0x72, // 1110010
    0x66, // 1100110
    0x6C, // 1101100
    0x42, // 1000010
    0x5C, // 1011100
    0x4E, // 1001110
    0x50, // 1010000
    0x44, // 1000100
    0x48, // 1001000
    0x74  // 1110100
)

@Composable
fun BarcodeScreen(modifier: Modifier = Modifier) {
    var input by rememberSaveable { mutableStateOf("036000291452") }
    var generatedCode by rememberSaveable { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    Column(modifier = modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text = "Generátor UPC-A", style = MaterialTheme.typography.headlineMedium)
        Text(text = "Zadej 12 číslic včetně poslední kontrolní číslice.")

        OutlinedTextField(value = input, onValueChange = { newValue ->
                input = newValue
                error = null
                generatedCode = null
            },
            modifier = Modifier.fillMaxWidth(), label = { Text("UPC-A kód") }, singleLine = true, isError = error != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), supportingText = { Text(text = error ?: "${input.length}/12 číslic") })

        Button(onClick = {
                val validationError = validateUpcA(input)

                error = validationError
                generatedCode = if (validationError == null) {
                    input
                } else {
                    null
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Vygenerovat čárový kód") }

        generatedCode?.let { code ->
            Spacer(modifier = Modifier.height(8.dp))

            UpcABarcode(code = code, modifier = Modifier.fillMaxWidth().height(180.dp))

            Text(text = code, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

// vraci null pro platny kód, jinak text chyby
private fun validateUpcA(code: String): String? {
    if (code.length != 12) {
        return "Kód musí obsahovat přesně 12 číslic."
    }

    if (code.any { it !in '0'..'9' }) {
        return "Kód může obsahovat pouze číslice 0–9."
    }

    val expectedCheckDigit = calculateCheckDigit(code.take(11))
    val actualCheckDigit = code.last().digitToInt()

    if (actualCheckDigit != expectedCheckDigit) {
        return "Nesprávná kontrolní číslice. Poslední číslice má být $expectedCheckDigit."
    }

    return null
}

// vypocita kontrolní cislici z prvních 11 cislic
private fun calculateCheckDigit(firstEleven: String): Int {
    require(firstEleven.length == 11 && firstEleven.all { it in '0'..'9' })

    var sum = 0

    firstEleven.forEachIndexed { index, character ->
        val digit = character.digitToInt()

        sum += if (index % 2 == 0) {
            digit * 3
        } else {
            digit
        }
    }

    return (10 - sum % 10) % 10
}

@Composable
fun UpcABarcode(code: String, modifier: Modifier = Modifier) {
    require(code.length == 12 && code.all { it in '0'..'9' }) {
        "UPC-A musí obsahovat přesně 12 číslic."
    }

    Canvas(modifier = modifier.background(Color.White)) {
        val barcodeModules = 95
        val quietZoneModules = 10
        val totalModules = barcodeModules + quietZoneModules * 2
        val moduleWidth = size.width / totalModules
        val topPadding = 8.dp.toPx()
        val bottomPadding = 8.dp.toPx()
        val guardExtension = 12.dp.toPx()
        val guardHeight = (size.height - topPadding - bottomPadding).coerceAtLeast(0f)
        val normalHeight = (guardHeight - guardExtension).coerceAtLeast(0f)
        var moduleIndex = quietZoneModules

        // cte bity od nejvysziho k nejnissimu.
        // bit 1 = cerny pruh, bit 0 = bila mezera.
        fun drawPattern(pattern: Int, bitCount: Int, isGuard: Boolean = false) {
            for (bitIndex in bitCount - 1 downTo 0) {
                val bit = (pattern shr bitIndex) and 1

                if (bit == 1) {
                    drawRect(color = Color.Black, topLeft = Offset(x = moduleIndex * moduleWidth, y = topPadding),
                        size = Size(width = moduleWidth, height = if (isGuard) {
                                guardHeight
                            } else {
                                normalHeight
                            }
                        )
                    )
                }
                moduleIndex++
            }
        }

        // pocatecni oddelovac: 101.
        drawPattern(pattern = 0b101, bitCount = 3, isGuard = true)

        // prvnich 6 cislic pouziva L vzory.
        for (index in 0 until 6) {
            val digit = code[index].digitToInt()

            drawPattern(pattern = Lp[digit], bitCount = 7)
        }

        // prostredni oddelovac: 01010.
        drawPattern(pattern = 0b01010, bitCount = 5, isGuard = true)

        // poslednich 6 cislic pouziva R vzory.
        for (index in 6 until 12) {
            val digit = code[index].digitToInt()

            drawPattern(pattern = Rp[digit], bitCount = 7)
        }

        // koncovy oddelovac: 101.
        drawPattern(pattern = 0b101, bitCount = 3, isGuard = true)
    }
}
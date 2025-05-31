package com.example.studentcardsystem

import android.nfc.Tag
import android.nfc.tech.NfcF
import java.io.IOException
import java.nio.charset.Charset

class FelicaReader {
    companion object {
        private const val SYS_CODE = 0xFE00
        private const val SERVICE_CODE = 0x1A8B
        
        fun readStudentId(tag: Tag): String? {
            val nfcF = NfcF.get(tag) ?: return null
            
            try {
                nfcF.connect()
                
                // Polling command to get IDm and PMm
                val pollingCommand = byteArrayOf(
                    0x00.toByte(), // Length (will be set later)
                    0x00.toByte(), // Polling command
                    (SYS_CODE shr 8).toByte(),
                    (SYS_CODE and 0xFF).toByte(),
                    0x01.toByte(), // Request code
                    0x00.toByte()  // Time slot
                )
                pollingCommand[0] = pollingCommand.size.toByte()
                
                val pollingResponse = nfcF.transceive(pollingCommand)
                
                if (pollingResponse.size >= 18) {
                    // Extract IDm (8 bytes starting from index 2)
                    val idm = pollingResponse.sliceArray(2..9)
                    
                    // Read without encryption command
                    val readCommand = byteArrayOf(
                        0x00.toByte(), // Length (will be set later)
                        0x06.toByte(), // Read without encryption command
                        *idm, // IDm (8 bytes)
                        0x01.toByte(), // Number of services
                        (SERVICE_CODE and 0xFF).toByte(),
                        ((SERVICE_CODE shr 8) and 0xFF).toByte(),
                        0x01.toByte(), // Number of blocks
                        0x80.toByte(), // Block list element (2-byte block number)
                        0x00.toByte()  // Block number (0)
                    )
                    readCommand[0] = readCommand.size.toByte()
                    
                    val readResponse = nfcF.transceive(readCommand)
                    
                    if (readResponse.size >= 16) {
                        // Extract data (16 bytes starting from index 11)
                        val data = readResponse.sliceArray(11 until readResponse.size)
                        val studentData = String(data, Charset.forName("Shift_JIS"))
                        
                        // Extract student ID using regex pattern
                        val regex = Regex("\\d{2}[A-Z]{2,3}\\d{3}")
                        val match = regex.find(studentData)
                        return match?.value
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                try {
                    nfcF.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
            
            return null
        }
    }
}

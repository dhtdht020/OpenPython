package kr.pe.ecmaxp.openpie.arch

import kr.pe.ecmaxp.thumbsf.exc.InvalidMemoryException
import li.cil.oc.api.Driver
import li.cil.oc.api.driver.item.Memory
import li.cil.oc.api.machine.Architecture
import li.cil.oc.api.machine.ExecutionResult
import li.cil.oc.api.machine.Machine
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.FileTime

@Suppress("unused")
@Architecture.Name("micropython (OpenPie)")
class OpenPieArchitecture(private val machine: Machine) : Architecture {
    private var initialized: Boolean = false

    private var totalMemory = 0
    private var vmMemory = 0
    private var vm: OpenPieVirtualMachine? = null
    private var lastSynchronizedResult: ExecutionResult? = null

    override fun isInitialized(): Boolean {
        return vm != null
    }

    override fun recomputeMemory(iterable: Iterable<ItemStack>): Boolean {
        var totalRam = 0.0
        for (stack in iterable) {
            val driver = Driver.driverFor(stack)
            if (driver is Memory) {
                totalRam += driver.amount(stack) * 1024
            }
        }

        totalMemory = totalRam.toInt()
        return (vm?.memorySize ?: 0) <= totalRam
    }

    // TODO: report exception handler?

    override fun initialize(): Boolean {
        close()

        try {
            vm = OpenPieVirtualMachine(machine, totalMemory)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return isInitialized
    }

    override fun close() {
        vm?.close()
        vm = null
    }

    override fun runSynchronized() {
        lastSynchronizedResult = try {
            vm!!.step(true)
        } catch (e: Exception) {
            e.printStackTrace()
            ExecutionResult.Error(e.toString())
        }

    }

    override fun runThreaded(isSynchronizedReturn: Boolean): ExecutionResult? {
        try {
            val prev = DebugFirmwareGetLastModifiedTime()
            val result: ExecutionResult?

            if (!isSynchronizedReturn) {
                // calling
                try {
                    result = vm!!.step(false)
                } catch (e: Exception) {
                    e.printStackTrace()
                    return ExecutionResult.Error(e.toString())
                }

                val next = DebugFirmwareGetLastModifiedTime()
                return if (prev != null && prev != next) ExecutionResult.Shutdown(true) else result
            } else {
                result = lastSynchronizedResult
                lastSynchronizedResult = null
                return result
            }
        } catch (e: Exception) {
            e.printStackTrace();
            return ExecutionResult.Error(e.toString())
        } catch (e: Throwable) {
            e.printStackTrace();
            throw e;
        }
    }

    private fun DebugFirmwareGetLastModifiedTime(): FileTime? {
        val file = File("C:\\Users\\EcmaXp\\Dropbox\\Projects\\openpie\\oprom\\build\\firmware.bin")
        try {
            return Files.getLastModifiedTime(file.toPath())
        } catch (ignored: IOException) {
        }

        return null
    }

    override fun onSignal() {
        vm!!.onSignal()
    }

    override fun onConnect() {
        println(toString() + ": onConnect()")
    }

    override fun load(nbtTagCompound: NBTTagCompound) {
        // System.out.println(toString() + ": loadNBT()");
    }

    override fun save(nbtTagCompound: NBTTagCompound) {
        // System.out.println(toString() + ": saveNBT()");
    }

    override fun toString(): String {
        return "OpenPieArchitecture(machine=$machine, initialized=$initialized, vm=$vm, lastSynchronizedResult=$lastSynchronizedResult)"
    }
}

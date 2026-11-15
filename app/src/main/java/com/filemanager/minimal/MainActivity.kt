package com.filemanager.minimal

import android.app.AlertDialog
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var list: RecyclerView
    private lateinit var location: TextView
    private var folder = Environment.getExternalStorageDirectory()
    private var items = emptyList<File>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 16, 18, 10)
        }
        setContentView(root)
        val heading = TextView(this).apply {
            text = "File Manager"
            textSize = 25f
            setPadding(0, 0, 0, 12)
        }
        root.addView(heading)
        location = TextView(this).apply { textSize = 11f; setPadding(0, 8, 0, 8) }
        root.addView(location)
        list = RecyclerView(this).apply { layoutManager = LinearLayoutManager(this@MainActivity) }
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        val actions = LinearLayout(this).apply { gravity = Gravity.CENTER }
        actions.addView(Button(this).apply {
            text = "New folder"
            setOnClickListener { createFolder() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(Button(this).apply {
            text = "Sort"
            setOnClickListener { sortDialog() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(actions)

        if (android.os.Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            AlertDialog.Builder(this)
                .setTitle("File access permission")
                .setMessage("Allow all-files access for File Manager in Android settings.")
                .setPositiveButton("OK", null)
                .show()
        } else refresh()
    }

    override fun onResume() {
        super.onResume()
        if (::list.isInitialized && (android.os.Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager())) refresh()
    }

    private fun refresh() {
        items = folder.listFiles()?.toList() ?: emptyList()
        location.text = folder.absolutePath
        list.adapter = FileRows(items, { file ->
            if (file.isDirectory) { folder = file; refresh() }
            else AlertDialog.Builder(this).setMessage("File: ${file.name}").setPositiveButton("OK", null).show()
        }, { file -> fileMenu(file) })
    }

    private fun createFolder() {
        val input = EditText(this).apply { hint = "Folder name" }
        AlertDialog.Builder(this).setTitle("Create folder").setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty() && File(folder, name).mkdir()) refresh()
                else Toast.makeText(this, "Could not create folder", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun sortDialog() {
        val choices = arrayOf("Name", "Newest first", "Largest first")
        AlertDialog.Builder(this).setTitle("Sort files").setItems(choices) { _, index ->
            items = when (index) {
                1 -> items.sortedByDescending { it.lastModified() }
                2 -> items.sortedByDescending { it.length() }
                else -> items.sortedBy { it.name.lowercase(Locale.getDefault()) }
            }
            list.adapter = FileRows(items, { f -> if (f.isDirectory) { folder = f; refresh() } }, { f -> fileMenu(f) })
        }.show()
    }

    private fun fileMenu(file: File) {
        val choices = arrayOf("Rename", "Delete", "Details")
        AlertDialog.Builder(this).setTitle(file.name).setItems(choices) { _, index ->
            when (index) {
                0 -> {
                    val input = EditText(this).apply { setText(file.name) }
                    AlertDialog.Builder(this).setTitle("Rename").setView(input)
                        .setPositiveButton("Save") { _, _ ->
                            val newName = input.text.toString().trim()
                            if (newName.isNotEmpty()) file.renameTo(File(file.parentFile, newName))
                            refresh()
                        }.setNegativeButton("Cancel", null).show()
                }
                1 -> AlertDialog.Builder(this).setMessage("Delete ${file.name}?")
                    .setPositiveButton("Delete") { _, _ -> file.deleteRecursively(); refresh() }
                    .setNegativeButton("Cancel", null).show()
                2 -> AlertDialog.Builder(this).setMessage(
                    "Name: ${file.name}\nSize: ${file.length()} bytes\nModified: ${date(file.lastModified())}"
                ).setPositiveButton("OK", null).show()
            }
        }.show()
    }

    private fun date(value: Long): String =
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(value))
}

private class FileRows(
    private val files: List<File>,
    private val onTap: (File) -> Unit,
    private val onLongTap: (File) -> Unit
) : RecyclerView.Adapter<FileRows.Row>() {
    class Row(val box: LinearLayout, val name: TextView, val info: TextView) : RecyclerView.ViewHolder(box)

    override fun onCreateViewHolder(parent: android.view.ViewGroup, type: Int): Row {
        val context = parent.context
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 12, 14, 12)
        }
        val name = TextView(context).apply { textSize = 15f; maxLines = 1 }
        val info = TextView(context).apply { textSize = 11f }
        box.addView(name)
        box.addView(info)
        return Row(box, name, info)
    }

    override fun onBindViewHolder(holder: Row, position: Int) {
        val file = files[position]
        holder.name.text = (if (file.isDirectory) "📁  " else "📄  ") + file.name
        holder.info.text = if (file.isDirectory) "${file.list()?.size ?: 0} items"
        else "${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(file.lastModified()))} • ${file.length()} bytes"
        holder.box.setOnClickListener { onTap(file) }
        holder.box.setOnLongClickListener { onLongTap(file); true }
    }

    override fun getItemCount() = files.size
}

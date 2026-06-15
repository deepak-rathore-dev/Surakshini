package com.drxrathore.surakshini

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TrustedContactsActivity : AppCompatActivity() {

    private lateinit var rvContacts: RecyclerView
    private lateinit var btnAddContact: Button
    private lateinit var contactAdapter: ContactAdapter
    private val contactList = mutableListOf<Contact>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_trusted_contacts)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        rvContacts = findViewById(R.id.rvContacts)
        rvContacts.layoutManager = LinearLayoutManager(this)

        contactAdapter = ContactAdapter(contactList) { contactToDelete ->
            deleteContact(contactToDelete)
        }
        rvContacts.adapter = contactAdapter

        btnAddContact = findViewById(R.id.btnAddContact)
        btnAddContact.setOnClickListener {
            showAddContactDialog()
        }

        loadContacts()
    }

    private fun loadContacts() {
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = AppDatabase.getDatabase(this@TrustedContactsActivity).contactDao()
            val contacts = dao.getAllContacts()
            withContext(Dispatchers.Main) {
                contactList.clear()
                contactList.addAll(contacts)
                contactAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun showAddContactDialog() {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(60, 40, 60, 20)

        val nameInput = EditText(this)
        nameInput.hint = "Name (e.g., Mom, Best Friend)"
        nameInput.setTextColor(Color.WHITE)
        nameInput.setHintTextColor(Color.GRAY)

        val phoneInput = EditText(this)
        phoneInput.hint = "Phone Number"
        phoneInput.inputType = InputType.TYPE_CLASS_PHONE
        phoneInput.setTextColor(Color.WHITE)
        phoneInput.setHintTextColor(Color.GRAY)

        layout.addView(nameInput)
        layout.addView(phoneInput)

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Add Trusted Contact")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val name = nameInput.text.toString().trim()
                val phone = phoneInput.text.toString().trim()
                if (name.isNotEmpty() && phone.isNotEmpty()) {
                    saveContact(name, phone)
                } else {
                    Toast.makeText(this, "Please enter both details", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveContact(name: String, phone: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = AppDatabase.getDatabase(this@TrustedContactsActivity).contactDao()
            dao.insertContact(Contact(name = name, phoneNumber = phone))
            loadContacts()
        }
    }

    private fun deleteContact(contact: Contact) {
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = AppDatabase.getDatabase(this@TrustedContactsActivity).contactDao()
            dao.deleteContact(contact)
            loadContacts()
        }
    }

    inner class ContactAdapter(
        private val contacts: List<Contact>,
        private val onDeleteClick: (Contact) -> Unit
    ) : RecyclerView.Adapter<ContactAdapter.ContactViewHolder>() {

        inner class ContactViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvContactName)
            val tvPhone: TextView = view.findViewById(R.id.tvContactPhone)
            val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteContact)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_contact, parent, false)
            return ContactViewHolder(view)
        }

        override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
            val contact = contacts[position]
            holder.tvName.text = contact.name
            holder.tvPhone.text = contact.phoneNumber
            holder.btnDelete.setOnClickListener { onDeleteClick(contact) }
        }

        override fun getItemCount(): Int = contacts.size
    }
}
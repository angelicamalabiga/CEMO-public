package com.example.cemo_loginpage

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.ChipGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Locale

class TransactionHistoryActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val pageSize = 30L

    private val loadedDocs = mutableListOf<DocumentSnapshot>()
    private var lastDoc: DocumentSnapshot? = null
    private var isLoading = false
    private var allLoaded = false
    private var currentFilter = Filter.ALL
    private var searchQuery = ""

    private val items = mutableListOf<HistoryItem>()
    private lateinit var adapter: HistoryAdapter
    private lateinit var tvEmpty: TextView

    enum class Filter { ALL, RECYCLING, REWARDS, EXPIRED }

    data class HistoryItem(
        val dateHeader: String?,
        val label: String,
        val weight: String,
        val time: String,
        val points: String,
        val pointsColor: Int
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction_history)

        tvEmpty = findViewById(R.id.tvEmpty)
        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        adapter = HistoryAdapter(items)
        val rv = findViewById<RecyclerView>(R.id.rvHistory)
        val lm = LinearLayoutManager(this)
        rv.layoutManager = lm
        rv.adapter = adapter
        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                if (lm.findLastVisibleItemPosition() >= items.size - 5) loadMore()
            }
        })

        findViewById<ChipGroup>(R.id.chipGroupFilter).setOnCheckedStateChangeListener { _, ids ->
            currentFilter = when (ids.firstOrNull()) {
                R.id.chipRecycling -> Filter.RECYCLING
                R.id.chipRewards   -> Filter.REWARDS
                R.id.chipExpired   -> Filter.EXPIRED
                else               -> Filter.ALL
            }
            rebuildItems()
        }

        findViewById<EditText>(R.id.etSearch).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString()?.trim()?.lowercase() ?: ""
                rebuildItems()
            }
        })

        loadMore()
    }

    private fun loadMore() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (isLoading || allLoaded) return
        isLoading = true

        var q = db.collection("users").document(uid)
            .collection("recyclingRecords")
            .orderBy("scanned_at", Query.Direction.DESCENDING)
            .limit(pageSize)
        lastDoc?.let { q = q.startAfter(it) }

        q.get()
            .addOnSuccessListener { snap ->
                if (snap.size() < pageSize) allLoaded = true
                if (!snap.isEmpty) {
                    lastDoc = snap.documents.last()
                    loadedDocs.addAll(snap.documents)
                }
                isLoading = false
                rebuildItems()
            }
            .addOnFailureListener { isLoading = false }
    }

    private fun categoryOf(doc: DocumentSnapshot): Filter =
        when (doc.getString("trash_type")) {
            "reward_claim", "reward_refund" -> Filter.REWARDS
            "points_expired"                -> Filter.EXPIRED
            else                            -> Filter.RECYCLING
        }

    private fun rebuildItems() {
        val dateFmt = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
        val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault())
        fun matchesSearch(doc: DocumentSnapshot): Boolean {
            if (searchQuery.isEmpty()) return true
            val date = doc.getTimestamp(
                "scanned_at", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )?.toDate()
            val haystack = listOf(
                doc.getString("trash_type") ?: "",
                doc.getString("rewardName") ?: "",
                doc.getString("note") ?: "",
                date?.let { dateFmt.format(it) } ?: ""
            ).joinToString(" ").lowercase().replace("_", " ")
            return haystack.contains(searchQuery)
        }

        val filtered = loadedDocs.filter {
            (currentFilter == Filter.ALL || categoryOf(it) == currentFilter) && matchesSearch(it)
        }
        items.clear()
        var lastDate = ""
        filtered.forEach { doc ->
            val type       = doc.getString("trash_type") ?: "—"
            val weightG    = doc.getDouble("weight_g") ?: 0.0
            val points     = doc.getDouble("points") ?: 0.0
            val note       = doc.getString("note") ?: ""
            val rewardName = doc.getString("rewardName") ?: ""
            val date = doc.getTimestamp(
                "scanned_at", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )?.toDate()

            val dateStr = date?.let { dateFmt.format(it) } ?: "—"
            val header = if (dateStr != lastDate) dateStr else null
            lastDate = dateStr

            val label = when (type) {
                "reward_refund"     -> "Refunded: " + rewardName.ifEmpty { note.removePrefix("Refunded: ").ifEmpty { "Reward" } }
                "reward_claim"      -> rewardName.ifEmpty { note.removePrefix("Claimed: ").ifEmpty { "Reward Claimed" } }
                "points_expired"    -> "Points Expired"
                "recycling_session" -> "Recycling Session"
                else                -> type
            }
            val weight = when (type) {
                "reward_refund"  -> "Refund"
                "reward_claim"   -> "Rewards"
                "points_expired" -> "Expired"
                else             -> "${String.format("%.1f", weightG)}g"
            }
            val ptsText = when (type) {
                "reward_claim", "points_expired" -> "${String.format("%.2f", points)} pts"
                else -> "+${String.format("%.2f", points)} pts"
            }
            val ptsColor = if (type == "reward_claim" || type == "points_expired")
                Color.parseColor("#D32F2F") else Color.parseColor("#2E7D32")

            items.add(HistoryItem(header, label, weight, date?.let { timeFmt.format(it) } ?: "", ptsText, ptsColor))
        }

        adapter.notifyDataSetChanged()
        tvEmpty.visibility = if (items.isEmpty() && allLoaded) View.VISIBLE else View.GONE

        // kung konti pa lang ang lumabas dahil sa filter, kumuha pa ng next page
        if (items.size < 15 && !allLoaded) loadMore()
    }

    class HistoryAdapter(private val data: List<HistoryItem>) :
        RecyclerView.Adapter<HistoryAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val header: TextView = v.findViewById(R.id.tvDateHeader)
            val label: TextView  = v.findViewById(R.id.tvLabel)
            val weight: TextView = v.findViewById(R.id.tvWeight)
            val time: TextView   = v.findViewById(R.id.tvTime)
            val pts: TextView    = v.findViewById(R.id.tvPts)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LayoutInflater.from(parent.context).inflate(R.layout.item_history_row, parent, false)
        )

        override fun getItemCount() = data.size

        override fun onBindViewHolder(h: VH, position: Int) {
            val item = data[position]
            h.header.visibility = if (item.dateHeader != null) View.VISIBLE else View.GONE
            h.header.text = item.dateHeader ?: ""
            h.label.text = item.label
            h.weight.text = item.weight
            h.time.text = item.time
            h.pts.text = item.points
            h.pts.setTextColor(item.pointsColor)
        }
    }
}
package jp.juggler.util

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import jp.juggler.character.SSRItem
import jp.juggler.screenshotbutton.R

class SSRListAdapter (val context: Context, val ssrList: ArrayList<SSRItem>) : BaseAdapter() {
    @SuppressLint("ViewHolder", "SetTextI18n", "InflateParams")
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view : View
        val holder : ViewHolder

        if (convertView == null) {
            view = LayoutInflater.from(context).inflate(R.layout.listitem, null)
            holder = ViewHolder()
            holder.ssrPercent = view.findViewById(R.id.ssr_percent)
            holder.ssrName = view.findViewById(R.id.ssr_name)
            holder.ssrTag = view.findViewById(R.id.ssr_tag)

            view.tag = holder
        } else {
            view = convertView
            holder = view.tag as ViewHolder
        }

        val ssr = ssrList[position]

        holder.ssrPercent?.text = "${ssr.percent}%"
        holder.ssrName?.text = ssr.name
        holder.ssrTag?.text = ssr.tag
        /* holder와 실제 데이터를 연결한다. null일 수 있으므로 변수에 '?'을 붙여 safe call 한다. */

        return view
    }

    override fun getItem(position: Int): Any {
        return ssrList[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getCount(): Int {
        return ssrList.size
    }

    class ViewHolder {
        var ssrPercent : TextView? = null
        var ssrName: TextView? = null
        var ssrTag: TextView? = null
    }
}
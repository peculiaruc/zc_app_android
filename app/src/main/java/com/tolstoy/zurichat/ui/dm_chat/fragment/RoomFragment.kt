package com.tolstoy.zurichat.ui.dm_chat.fragment

import android.os.Bundle
import android.text.format.DateUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.tolstoy.zurichat.databinding.FragmentDmBinding
import com.tolstoy.zurichat.models.User
import com.tolstoy.zurichat.ui.add_channel.BaseItem
import com.tolstoy.zurichat.ui.add_channel.BaseListAdapter
import com.tolstoy.zurichat.ui.dm.response.RoomListResponseItem
import com.tolstoy.zurichat.ui.dm_chat.model.request.SendMessageBody
import com.tolstoy.zurichat.ui.dm_chat.model.response.message.BaseRoomData
import com.tolstoy.zurichat.ui.dm_chat.model.response.message.Data
import com.tolstoy.zurichat.ui.dm_chat.model.response.message.SendMessageResponse
import com.tolstoy.zurichat.ui.dm_chat.viewmodel.RoomViewModel
import com.tolstoy.zurichat.ui.fragments.channel_chat.ChannelHeaderItem
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.random.Random


class RoomFragment : Fragment() {

    private lateinit var roomsListAdapter : BaseListAdapter
    private lateinit var roomId: String
    private lateinit var userId: String
    private lateinit var senderId: String
    private lateinit var user : User
    private lateinit var room : RoomListResponseItem
    private val roomMsgViewModel : RoomViewModel by viewModels()
    private lateinit var binding: FragmentDmBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        user = requireActivity().intent.extras?.getParcelable("USER")!!
        room = requireActivity().intent.extras?.getParcelable("room")!!
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val channelChatEdit = binding.channelChatEditText           //get message from this edit text
        val sendVoiceNote = binding.sendVoiceBtn
        val sendMessage = binding.sendMessageBtn                    //use this button to send the message
        val typingBar = binding.channelTypingBar
        val toolbar = binding.toolbarDm

        roomId = room._id
        userId = room.room_user_ids.first()
        senderId = room.room_user_ids.last()

        toolbar.title = roomId

        channelChatEdit.doOnTextChanged { text, start, before, count ->
            if (text.isNullOrEmpty()) {
                sendMessage.isEnabled = false
                sendVoiceNote.isEnabled = true
            } else {
                sendMessage.isEnabled = true
                sendVoiceNote.isEnabled = false
            }
        }

        roomsListAdapter = BaseListAdapter {
        }

        binding.listDm.adapter = roomsListAdapter
        binding.listDm.itemAnimator = null


        roomMsgViewModel.getMessages()

        roomMsgViewModel.myGetMessageResponse.observe(viewLifecycleOwner, { response ->
            if (response.isSuccessful) {
                val messageResponse = response.body()
                messageResponse?.results?.forEach{
                    val newBaseRoomData = BaseRoomData(it, null, true)
                    messagesArrayList.add(newBaseRoomData)
                }
                createMessagesList(messagesArrayList).let {
                    roomsListAdapter.submitList(it)
                }

            } else {
                when (response.code()) {
                    400 -> {
                        Log.e("Error 400", "invalid authorization")
                    }
                    404 -> {
                        Log.e("Error 404", "Not Found")
                    }
                    401 -> {
                        Log.e("Error 401", "No authorization or session expired")
                    }
                    else -> {
                        Log.e("Error", "Generic Error")
                    }
                }
            }
        })


        sendMessage.setOnClickListener{
            if (channelChatEdit.text.toString().isNotEmpty()){
                val s = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
                s.timeZone = TimeZone.getTimeZone("UTC")
                val time = s.format(Date(System.currentTimeMillis()))

                val message = channelChatEdit.text.toString()
                val dataMessage = Data(time, message, senderId)
                val sendMessageResponse = SendMessageResponse(dataMessage, "message_create", generateID().toString(), roomId, "201", false)
                val baseRoomData = BaseRoomData(null, sendMessageResponse, false)
                messagesArrayList.add(baseRoomData)

                val messagesWithDateHeaders = createMessagesList(messagesArrayList).let {
                    roomsListAdapter.submitList(it)
                }
                val messageBody = SendMessageBody(message, roomId, senderId )
                roomMsgViewModel.sendMessages(messageBody)
                roomMsgViewModel.mySendMessageResponse.observe(viewLifecycleOwner, { response ->
                    if (response.isSuccessful) {
                        val messageResponse = response.body()
                        val position = messagesArrayList.indexOf(baseRoomData)
                        val newBaseRoomData = BaseRoomData(null, messageResponse, false)
                        messagesArrayList[position] = newBaseRoomData
                        createMessagesList(messagesArrayList).let {
                            roomsListAdapter.submitList(it)
                        }
                        Log.i("Message Response", "$messageResponse")
                    } else {
                        when (response.code()) {
                            400 -> {
                                Log.e("Error 400", "invalid authorization")
                            }
                            404 -> {
                                Log.e("Error 404", "Not Found")
                            }
                            401 -> {
                                Log.e("Error 401", "No authorization or session expired")
                            }
                            else -> {
                                Log.e("Error", "Generic Error")
                            }
                        }
                    }
                })
                channelChatEdit.text?.clear()
            }
        }

        toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressed()
        }
    }

    private var messagesArrayList: ArrayList<BaseRoomData> = ArrayList()
    private fun createMessagesList(rooms: List<BaseRoomData>): MutableList<BaseItem<*>> {
        // Wrap data in list items
        val roomsItems = rooms.map {
            RoomListItem(it, user,requireActivity())
        }

        val roomsWithDateHeaders = mutableListOf<BaseItem<*>>()
        // Loop through the channels list and add headers where we need them
        var currentHeader: String? = null

        roomsItems.forEach{ c->
            if (c.data.checkMessage){
                val dateString = DateUtils.getRelativeTimeSpanString(convertStringDateToLong(c.data.getMessageResponse!!.created_at),
                    Calendar.getInstance().timeInMillis,
                    DateUtils.DAY_IN_MILLIS)
                dateString.toString().let {
                    if (it != currentHeader){
                        roomsWithDateHeaders.add(ChannelHeaderItem(it))
                        currentHeader = it
                    }
                }
                roomsWithDateHeaders.add(c)
            } else {
                val dateString = DateUtils.getRelativeTimeSpanString(convertStringDateToLong(c.data.sendMessageResponse!!.data.created_at),
                    Calendar.getInstance().timeInMillis,
                    DateUtils.DAY_IN_MILLIS)
                dateString.toString().let {
                    if (it != currentHeader){
                        roomsWithDateHeaders.add(ChannelHeaderItem(it))
                        currentHeader = it
                    }
                }
                roomsWithDateHeaders.add(c)
            }

        }

        return roomsWithDateHeaders
    }

    private fun convertStringDateToLong(date: String) : Long {
        val s = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
        s.timeZone = TimeZone.getTimeZone("UTC")
        var d = s.parse(date)
        return d.time
    }
    private fun generateID():Int{
        return Random(6000000).nextInt()
    }
}
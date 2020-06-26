package com.sendbird.desk.android.sample.activity.inbox;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.StyleableRes;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.sendbird.android.AdminMessage;
import com.sendbird.android.BaseChannel;
import com.sendbird.android.BaseMessage;
import com.sendbird.android.FileMessage;
import com.sendbird.android.SendBird;
import com.sendbird.android.SendBirdException;
import com.sendbird.android.UserMessage;
import com.sendbird.desk.android.Ticket;
import com.sendbird.desk.android.sample.R;
import com.sendbird.desk.android.sample.activity.chat.ChatActivity;
import com.sendbird.desk.android.sample.app.Event;
import com.sendbird.desk.android.sample.desk.DeskAdminMessage;
import com.sendbird.desk.android.sample.desk.DeskConnectionManager;
import com.sendbird.desk.android.sample.desk.DeskManager;
import com.sendbird.desk.android.sample.utils.DateUtils;
import com.sendbird.desk.android.sample.utils.image.ImageUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import static com.sendbird.desk.android.sample.desk.DeskManager.CONNECTION_HANDLER_ID_OPEN_TICKETS;
import static com.sendbird.desk.android.sample.desk.DeskManager.TICKET_HANDLER_ID_OPEN_TICKETS;

public class OpenTicketsFragment extends Fragment {

    private View mView;

    private LinearLayout mEmptyPlaceholder;
    private ProgressBar mProgressBar;

    private SwipeRefreshLayout mRefreshLayout;

    private ListView mListView;
    private ListAdapter mListAdapter;

    private int mOffset;
    private boolean mHasNext = true;
    private boolean mLoading;

    private OnTicketClosedListener mTicketClosedListener;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_open_list, container, false);

        mEmptyPlaceholder = (LinearLayout) view.findViewById(R.id.layout_empty_placeholder);
        mProgressBar = (ProgressBar) view.findViewById(R.id.progress_bar);
        mRefreshLayout = (SwipeRefreshLayout) view.findViewById(R.id.refresh_layout);
        mListView = (ListView) view.findViewById(R.id.list_view);

        mEmptyPlaceholder.setVisibility(View.INVISIBLE);
        mProgressBar.setVisibility(View.VISIBLE);

        mRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                loadOpenTickets(true);
            }
        });

        mListAdapter = new ListAdapter(getActivity(), R.layout.list_item_open_ticket);
        mListView.setAdapter(mListAdapter);
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                Ticket ticket = mListAdapter.getList().get(i);

                Map<String, String> data = new HashMap<>();
                data.put("title", ticket.getTitle());
                data.put("status", ticket.getStatus());
                data.put("ticket_id", String.valueOf(ticket.getId()));
                Event.onEvent(Event.EventListener.INBOX_OPEN_TICKET_SELECTED, data);

                Intent intent = new Intent(getActivity(), ChatActivity.class);
                intent.putExtra(ChatActivity.EXTRA_TITLE, ticket.getTitle());
                intent.putExtra(ChatActivity.EXTRA_CHANNEL_URL, ticket.getChannel().getUrl());
                startActivityForResult(intent, InboxActivity.REQUEST_EXIT);
            }
        });
        mListView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (visibleItemCount == totalItemCount) return;
                if (firstVisibleItem + visibleItemCount >= (int) (totalItemCount * 0.8f)) {
                    loadOpenTickets(false);
                }
            }
        });

        mView = view;
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        DeskManager.addTicketHandler(TICKET_HANDLER_ID_OPEN_TICKETS, new DeskManager.TicketHandler() {
            @Override
            public void onMessageReceived(BaseChannel baseChannel, BaseMessage baseMessage) {
                if (DeskAdminMessage.is(baseMessage)) {
                    if (DeskAdminMessage.isAssignType(baseMessage) || DeskAdminMessage.isTransferType(baseMessage)) {
                        // Updates ticket information without sorting.
                        Ticket.getByChannelUrl(baseChannel.getUrl(), new Ticket.GetByChannelUrlHandler() {
                            @Override
                            public void onResult(Ticket ticket, SendBirdException e) {
                                if (e != null) {
                                    return;
                                }

                                if (ticket != null) {
                                    mListAdapter.replace(ticket);
                                    mListAdapter.notifyDataSetChanged();
                                }
                            }
                        });
                    } else if (DeskAdminMessage.isCloseType(baseMessage)) {
                        // Moves ticket to closed ticket list.
                        Ticket.getByChannelUrl(baseChannel.getUrl(), new Ticket.GetByChannelUrlHandler() {
                            @Override
                            public void onResult(Ticket ticket, SendBirdException e) {
                                if (e != null) {
                                    return;
                                }

                                if (ticket != null) {
                                    mListAdapter.remove(ticket.getId());
                                    mListAdapter.notifyDataSetChanged();

                                    if (mTicketClosedListener != null) {
                                        mTicketClosedListener.onClosed(ticket);
                                    }

                                    if (mListAdapter.getList().size() == 0) {
                                        loadOpenTickets(true);
                                    }
                                }
                            }
                        });
                    }
                }
            }

            @Override
            public void onChannelChanged(BaseChannel baseChannel) {
                // Each ticket has serialized channel object (which is not singleton).
                // Therefore, ticket should be updated with the updated channel to match the unread message count.
                processMessage(baseChannel);
            }

            private void processMessage(BaseChannel baseChannel) {
                Ticket.getByChannelUrl(baseChannel.getUrl(), new Ticket.GetByChannelUrlHandler() {
                    @Override
                    public void onResult(Ticket ticket, SendBirdException e) {
                        if (e != null) {
                            return;
                        }

                        if (ticket != null && ticket.getChannel() != null) {
                            Ticket updatedTicket = null;
                            String ticketChannelUrl;
                            for (Ticket t : mListAdapter.getList()) {
                                ticketChannelUrl = (t.getChannel() != null) ? t.getChannel().getUrl() : "";
                                if (ticketChannelUrl.equals(ticket.getChannel().getUrl())) {
                                    mListAdapter.remove(t.getId());
                                    updatedTicket = ticket;
                                    break;
                                }
                            }

                            if (updatedTicket != null) {
                                mListAdapter.insert(ticket, 0);
                                mListAdapter.notifyDataSetChanged();
                            } else {
                                loadOpenTickets(true);
                            }
                        }
                    }
                });
            }
        });

        DeskConnectionManager.addConnectionManagementHandler(CONNECTION_HANDLER_ID_OPEN_TICKETS, new DeskConnectionManager.ConnectionManagementHandler() {
            @Override
            public void onConnected(boolean reconnect) {
                loadOpenTickets(true);
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        DeskManager.removeTicketHandler(TICKET_HANDLER_ID_OPEN_TICKETS);
        DeskConnectionManager.removeConnectionManagementHandler(CONNECTION_HANDLER_ID_OPEN_TICKETS);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == InboxActivity.REQUEST_EXIT) {
            if (resultCode == Activity.RESULT_OK) {
                if (getActivity() != null) {
                    getActivity().finish();
                }
            }
        }
    }

    void loadOpenTickets(final boolean refresh) {
        if (refresh) {
            mOffset = 0;
            mHasNext = true;
            mLoading = false;
        }

        if (!mHasNext || mLoading) {
            return;
        }

        mLoading = true;

        Ticket.getOpenedList(mOffset, new Ticket.GetOpenedListHandler() {
            @Override
            public void onResult(final List<Ticket> tickets, final boolean hasNext, SendBirdException e) {
                if (e != null) {
                    mLoading = false;
                    mProgressBar.setVisibility(View.INVISIBLE);
                    mRefreshLayout.setRefreshing(false);

                    if (SendBird.getConnectionState() == SendBird.ConnectionState.OPEN) {
                        Snackbar.make(mView, R.string.desk_failed_loading_open_tickets, Snackbar.LENGTH_SHORT).show();
                    }
                    return;
                }

                if (tickets.size() == 0) {
                    mLoading = false;
                    mEmptyPlaceholder.setVisibility(View.VISIBLE);
                    mProgressBar.setVisibility(View.INVISIBLE);
                    mRefreshLayout.setRefreshing(false);
                    mListAdapter.clear();
                    mListAdapter.notifyDataSetChanged();
                } else {
                    mLoading = false;
                    mOffset += tickets.size();
                    mHasNext = hasNext;

                    mEmptyPlaceholder.setVisibility(View.INVISIBLE);
                    mProgressBar.setVisibility(View.INVISIBLE);
                    mRefreshLayout.setRefreshing(false);

                    if (refresh) {
                        mListAdapter.clear();
                    }

                    mListAdapter.addAll(tickets);
                    mListAdapter.notifyDataSetChanged();
                }
            }
        });
    }

    void setOnTicketClosedListener(OnTicketClosedListener listener) {
        mTicketClosedListener = listener;
    }

    private class ListAdapter extends ArrayAdapter<Ticket> {
        private Context mContext;
        private ArrayList<Ticket> mTicketList;
        private int mResId;

        private ListAdapter(Context context, int resource) {
            super(context, resource);
            mContext = context;
            mResId = resource;
            mTicketList = new ArrayList<>();
        }

        private List<Ticket> getList() {
            return mTicketList;
        }

        private void replace(Ticket object) {
            int index = -1;

            for (Ticket ticket : mTicketList) {
                if (ticket.getId() == object.getId()) {
                    index = mTicketList.indexOf(ticket);
                    mTicketList.remove(ticket);
                    break;
                }
            }

            if (index != -1) {
                mTicketList.add(index, object);
            }
        }

        @Override
        public int getCount() {
            return mTicketList.size();
        }

        @Override
        public void addAll(Collection<? extends Ticket> collection) {
            super.addAll(collection);
            mTicketList.addAll(collection);
        }

        @Override
        public void add(Ticket object) {
            super.add(object);
            mTicketList.add(object);
        }

        @Override
        public void insert(Ticket object, int index) {
            super.insert(object, index);
            mTicketList.add(index, object);
        }

        void remove(long id) {
            for (Ticket ticket : mTicketList) {
                if (ticket.getId() == id) {
                    mTicketList.remove(ticket);
                    break;
                }
            }
        }

        @Override
        public void clear() {
            super.clear();
            mTicketList.clear();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;

            if (view == null) {
                LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                view = inflater.inflate(mResId, parent, false);

                ViewHolder viewHolder = new ViewHolder();
                viewHolder.setView("txt_title", view.findViewById(R.id.txt_title));
                viewHolder.setView("txt_last_message", view.findViewById(R.id.txt_last_message));
                viewHolder.setView("txt_last_update", view.findViewById(R.id.txt_last_update));
                viewHolder.setView("txt_unread_count", view.findViewById(R.id.txt_unread_count));
                viewHolder.setView("img_agent_profile", view.findViewById(R.id.img_agent_profile));
                viewHolder.setView("txt_agent_name", view.findViewById(R.id.txt_agent_name));

                view.setTag(viewHolder);
            }

            Ticket ticket = mTicketList.get(position);
            ViewHolder viewHolder = (ViewHolder) view.getTag();

            viewHolder.getView("txt_title", TextView.class).setText(ticket.getTitle());

            if (DeskManager.getLastMessage(ticket) != null) {
                BaseMessage message = DeskManager.getLastMessage(ticket);
                if (message == null) {
                    viewHolder.getView("txt_last_update", TextView.class).setText("");
                    viewHolder.getView("txt_last_message", TextView.class).setText("");
                } else if (message instanceof UserMessage) {
                    viewHolder.getView("txt_last_update", TextView.class).setText(DateUtils.formatDateTime(getActivity(), message.getCreatedAt()));
                    viewHolder.getView("txt_last_message", TextView.class).setText(((UserMessage) message).getMessage());
                } else if (message instanceof AdminMessage) {
                    viewHolder.getView("txt_last_update", TextView.class).setText(DateUtils.formatDateTime(getActivity(), message.getCreatedAt()));
                    viewHolder.getView("txt_last_message", TextView.class).setText(((AdminMessage) message).getMessage());
                } else if (message instanceof FileMessage) {
                    viewHolder.getView("txt_last_update", TextView.class).setText(DateUtils.formatDateTime(getActivity(), message.getCreatedAt()));
                    viewHolder.getView("txt_last_message", TextView.class).setText(R.string.desk_file_message);
                }

                if (DeskManager.getUnreadMessageCount(ticket) > 9) {
                    viewHolder.getView("txt_unread_count", TextView.class).setVisibility(View.VISIBLE);
                    viewHolder.getView("txt_unread_count", TextView.class).setText("9+");
                } else if (DeskManager.getUnreadMessageCount(ticket) > 0) {
                    viewHolder.getView("txt_unread_count", TextView.class).setVisibility(View.VISIBLE);
                    viewHolder.getView("txt_unread_count", TextView.class)
                            .setText(String.valueOf(DeskManager.getUnreadMessageCount(ticket)));
                } else {
                    viewHolder.getView("txt_unread_count", TextView.class).setVisibility(View.INVISIBLE);
                }
            } else {
                viewHolder.getView("txt_last_update", TextView.class).setText("");
                viewHolder.getView("txt_last_message", TextView.class).setText("");
                viewHolder.getView("txt_unread_count", TextView.class).setVisibility(View.INVISIBLE);
            }

            TypedArray ta = mContext.obtainStyledAttributes(
                    new int[]{R.attr.deskAvatarIcon,
                            R.attr.deskServiceIcon,
                            R.attr.deskServiceName}
            );
            @StyleableRes int deskAvatarIconIndex = 0;
            @StyleableRes int deskServiceIconIndex = 1;
            @StyleableRes int deskServiceNameIndex = 2;

            ImageView imageView = viewHolder.getView("img_agent_profile", ImageView.class);
            if (ticket.getAgent() != null) {
                ImageUtils.displayRoundImageFromUrlWithPlaceHolder(mContext,
                        ticket.getAgent().getProfileUrl(),
                        imageView,
                        ta.getResourceId(deskAvatarIconIndex, R.drawable.img_profile));
                viewHolder.getView("txt_agent_name", TextView.class).setText(ticket.getAgent().getName());
            } else {
                imageView.setImageDrawable(getResources().getDrawable(ta.getResourceId(deskServiceIconIndex, 0)));
                viewHolder.getView("txt_agent_name", TextView.class).setText(ta.getResourceId(deskServiceNameIndex, 0));
            }

            ta.recycle();

            return view;
        }

        private class ViewHolder {
            private Hashtable<String, View> holder = new Hashtable<>();

            private void setView(String k, View v) {
                holder.put(k, v);
            }

            private View getView(String k) {
                return holder.get(k);
            }

            private <T> T getView(String k, Class<T> type) {
                return type.cast(getView(k));
            }
        }
    }

    interface OnTicketClosedListener {
        void onClosed(Ticket ticket);
    }
}

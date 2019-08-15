package com.weather.airlock.sdk.ui;

/**
 * @author Denis Voloshin on 04/09/2017.
 */

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.ibm.airlock.common.AirlockCallback;
import com.ibm.airlock.common.notifications.AirlockNotification;
import com.ibm.airlock.common.services.InfraAirlockService;
import com.ibm.airlock.common.services.NotificationService;
import com.ibm.airlock.common.util.Constants;
import com.weather.airlock.sdk.R;
import com.weather.airlock.sdk.dagger.AirlockClientsManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Hashtable;
import java.util.Map;

import javax.inject.Inject;


public class NotificationsListFragment extends Fragment {

    //list of available notifications
    private ListView listView;

    //adapter for rendering
    private ArrayAdapter<String> adapter;

    //current branches list with  the selection choice for this device.
    private Map<String, String> notifications;

    //current branch name, by default is 'master'
    private String[] notificationsNames;

    @Inject
    NotificationService notificationService;

    @Inject
    InfraAirlockService infraAirlockService;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // init Dagger
        AirlockClientsManager.getAirlockClientDiComponent().inject(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.notifications_list, container, false);
        listView = (ListView) view.findViewById(R.id.list);

        //init list
        this.notifications = new Hashtable<>();

        infraAirlockService.getPersistenceHandler().write(Constants.SP_LAST_NOTIFICATIONS_FULL_DOWNLOAD_TIME, "");
        infraAirlockService.pullNotifications(new AirlockCallback() {
            @Override
            public void onFailure(Exception e) {
                final String error = String.format(getResources().getString(R.string.retrieving_notifications), e.getMessage());
                Log.e(Constants.LIB_LOG_TAG, error);
                showToast(error);
            }

            @Override
            public void onSuccess(String response) {

                //parse server response,the response has to be in json format
                try {
                    final JSONObject notificationsFullResponse = new JSONObject(response);
                    if (notificationsFullResponse.isNull("notifications")) {
                        String warning = getResources().getString(R.string.notifications_is_empty);
                        Log.w(Constants.LIB_LOG_TAG, warning);
                        showToast(warning);
                        return;
                    }
                    final JSONArray notificationsArray = notificationsFullResponse.getJSONArray("notifications");

                    if (getActivity() == null) {
                        return;
                    }

                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            notifications = generateNotificationsList(notificationsArray);
                            java.util.Set<String> keys = notifications.keySet();
                            notificationsNames = (keys.toArray(new String[keys.size()]));
                            Arrays.sort(notificationsNames);

                            adapter = new ArrayAdapter<String>(getActivity(),
                                    android.R.layout.simple_list_item_1, notificationsNames) {
                                @Override
                                public View getView(int position, @Nullable View convertView, ViewGroup parent) {
                                    convertView = new TextView(getContext());
                                    convertView.setPadding(20, 30, 20, 30);
                                    ((TextView) convertView).setTextSize(15);
                                    String notificationName = getItem(position);
                                    AirlockNotification notification = notificationService.getNotification(notificationName);
                                    if (notification != null) {
                                        if (notification.isEnabled() && notification.isProcessingEnabled()) {
                                            ((TextView) convertView).setTextColor(Color.BLUE);
                                        } else {
                                            ((TextView) convertView).setTextColor(Color.BLACK);
                                        }
                                        ((TextView) convertView).setText(notificationName);
                                    }
                                    return convertView;
                                }
                            };

                            //listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
                            listView.setAdapter(adapter);

                            LayoutInflater inflater = getActivity().getLayoutInflater();
                            ViewGroup header = (ViewGroup) inflater.inflate(R.layout.notifications_list_header, listView, false);
                            listView.addHeaderView(header);


                            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                                @Override
                                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                                    if (position > 0) {
                                        AirlockNotification notification = notificationService.getNotification(notificationsNames[position - 1]);
                                        if (notification == null) {
                                            showToast(getResources().getString(R.string.notification_not_available, notificationsNames[position - 1]));
                                            return;
                                        }
                                        //TODO: check if isAssociatedWithUserGroup logic is needed
                                        if (!notification.isEnabled()) {
                                            StringBuilder sbMessage = new StringBuilder();
                                            if (!notification.isEnabled()) {
                                                sbMessage.append("disabled");
                                            } else {
                                                sbMessage.append("not associated with any active user group");
                                            }
                                            showToast("Notification '" + notificationsNames[position - 1] + "' is " + sbMessage.toString());
                                            return;
                                        }

                                        FragmentManager manager = getFragmentManager();
                                        FragmentTransaction transaction = manager.beginTransaction();
                                        transaction.add(R.id.container, NotificationDetailFragment.newInstance(notificationsNames[position - 1]), NotificationsManagerActivity.NOTIFICATIONS_DETAILS_FRAGMENT);
                                        transaction.addToBackStack(null);
                                        transaction.commit();
                                    }
                                }
                            });
                        }
                    });
                } catch (Exception e) {
                    final String error = getResources().getString(R.string.notification_process_failed);
                    Log.e(Constants.LIB_LOG_TAG, error);
                    showToast(error);
                    Log.e(Constants.LIB_LOG_TAG, "");
                    //render only values from the cache empty list
                }
            }
        });

        return view;
    }

    private void showToast(final String msg) {
        Log.d(this.getClass().getName(), msg);
        if (getActivity() != null) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getActivity().getBaseContext(), msg,
                            Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private Map<String, String> generateNotificationsList(JSONArray branches) {
        Map<String, String> notificationsMap = new Hashtable<>();
        int branchesListLength = branches.length();
        for (int i = 0; i < branchesListLength; i++) {
            JSONObject branchJSON = branches.optJSONObject(i);
            if (branchJSON != null && branchJSON.has("name") && branchJSON.has("uniqueId")) {
                String name = branchJSON.optString("name");
                String uniqueId = branchJSON.optString("uniqueId");
                if (name != null && uniqueId != null) {
                    notificationsMap.put(name, uniqueId);
                }
            }
        }
        return notificationsMap;
    }
}


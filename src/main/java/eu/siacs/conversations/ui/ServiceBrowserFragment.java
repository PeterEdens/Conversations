/*
 * Copyright (C) 2015 Andriy Druk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.siacs.conversations.ui;

import eu.siacs.conversations.SpreedboxApplication;
import spreedbox.me.app.R;
import eu.siacs.conversations.ui.adapter.ServiceAdapter;
import com.github.druk.rxdnssd.BonjourService;
import com.github.druk.rxdnssd.RxDnssd;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.AppCompatButton;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import org.appspot.apprtc.util.NullHostNameVerifier;
import org.appspot.apprtc.util.TLSSocketFactory;

import java.net.HttpURLConnection;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.RejectedExecutionException;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

public class ServiceBrowserFragment<T> extends Fragment {

    private static final String KEY_REG_TYPE = "reg_type";
    private static final String KEY_DOMAIN = "domain";
    private static final String KEY_SELECTED_POSITION = "selected_position";

    protected Subscription mSubscription;
    protected ServiceAdapter mAdapter;
    protected String mReqType = "_workstation._tcp.";
    protected String mDomain = "local.";
    protected RecyclerView mRecyclerView;
    protected ProgressBar mProgressView;
    protected LinearLayout mErrorView;
    protected RxDnssd mRxDnssd;
    private AppCompatButton mButtonCancel;

    class CheckSpreedbox extends AsyncTask<String, Void, Boolean> {

        private Exception exception;
        public BonjourService bonjourService;

        protected Boolean doInBackground(String... urls) {
            return exists(urls[0]);
        }

        protected void onPostExecute(Boolean isSpreedbox) {
            if (isSpreedbox) {
                int itemsCount = mAdapter.getItemCount();
                mAdapter.add(bonjourService);
                ServiceBrowserFragment.this.showList(itemsCount);
                mAdapter.notifyDataSetChanged();
            }
        }
    }

    protected View.OnClickListener mListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            int position = mRecyclerView.getLayoutManager().getPosition(v);
            mAdapter.setSelectedItemId(mAdapter.getItemId(position));
            mAdapter.notifyDataSetChanged();
            if (ServiceBrowserFragment.this.isAdded()) {
                BonjourService service = mAdapter.getItem(position);
                ((ServiceListener) ServiceBrowserFragment.this.getActivity()).onServiceWasSelected(mDomain, mReqType, service);
            }
        }
    };

    public static Fragment newInstance(String domain, String regType) {
        return fillArguments(new ServiceBrowserFragment(), domain, regType);
    }

    protected static Fragment fillArguments(Fragment fragment, String domain, String regType) {
        Bundle bundle = new Bundle();
        bundle.putString(KEY_DOMAIN, domain);
        bundle.putString(KEY_REG_TYPE, regType);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        if (!(context instanceof ServiceListener)) {
            throw new IllegalArgumentException("Fragment context should implement ServiceListener interface");
        }

        mRxDnssd = SpreedboxApplication.getRxDnssd(context);
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mReqType = getArguments().getString(KEY_REG_TYPE);
            mDomain = getArguments().getString(KEY_DOMAIN);
        }
        mAdapter = new ServiceAdapter(getActivity()) {
            @Override
            public void onBindViewHolder(ViewHolder viewHolder, int i) {
                BonjourService service = getItem(i);
                String name = service.getServiceName();
                String mac = name.substring(name.indexOf('['));
                name = name.substring(0, name.indexOf('['));
                viewHolder.text1.setText(name);
                viewHolder.text3.setText(mac);
                
                if (service.getInet4Address() != null) {
                    viewHolder.text2.setText(service.getInet4Address().getHostAddress());
                }
                else if (service.getInet6Address() != null) {
                    viewHolder.text2.setText(service.getInet6Address().getHostAddress());
                }
                else {
                    viewHolder.text2.setText(R.string.not_resolved_yet);
                }
                viewHolder.itemView.setOnClickListener(mListener);
                viewHolder.itemView.setBackgroundResource(getBackground(i));
            }
        };
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        FrameLayout rootView = (FrameLayout) inflater.inflate(R.layout.fragment_service_browser, container, false);
        mRecyclerView = (RecyclerView) rootView.findViewById(R.id.recycler_view);
        mProgressView = (ProgressBar) rootView.findViewById(R.id.progress);
        mErrorView = (LinearLayout) rootView.findViewById(R.id.error_container);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(mRecyclerView.getContext()));
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setAdapter(mAdapter);
        if (savedInstanceState != null) {
            mAdapter.setSelectedItemId(savedInstanceState.getLong(KEY_SELECTED_POSITION, -1L));
        }
        mButtonCancel = (AppCompatButton) rootView.findViewById(R.id.buttonCancel);
        mButtonCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ((ServiceListener) ServiceBrowserFragment.this.getActivity()).onCancel();
            }
        });
        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();
        startDiscovery();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopDiscovery();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(KEY_SELECTED_POSITION, mAdapter.getSelectedItemId());
    }


    public static boolean exists(String URLName){

        X509TrustManager trustManager = new X509TrustManager() {

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {
                // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
                // NOTE : This is where we can calculate the certificate's fingerprint,
                // show it to the user and throw an exception in case he doesn't like it
            }

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {
            }
        };

// Create a trust manager that does not validate certificate chains
        X509TrustManager[] trustAllCerts = new X509TrustManager[] { trustManager
        };

// Install the all-trusting trust manager
        SSLSocketFactory noSSLv3Factory = null;
        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
                noSSLv3Factory = new TLSSocketFactory(trustAllCerts, new SecureRandom());
            } else {
                noSSLv3Factory = sc.getSocketFactory();
            }
            HttpsURLConnection.setDefaultSSLSocketFactory(noSSLv3Factory);
        } catch (GeneralSecurityException e) {
        }

        try {
            HttpsURLConnection.setFollowRedirects(false);
            // note : you may also need
            //        HttpURLConnection.setInstanceFollowRedirects(false)
            URL url = new URL(URLName);
            HttpsURLConnection con =
                    (HttpsURLConnection) url.openConnection();
            con.setSSLSocketFactory(noSSLv3Factory);
            con.setRequestProperty( "Accept-Encoding", "" );
            //HttpsURLConnection.setDefaultHostnameVerifier(new NullHostNameVerifier());
            con.setHostnameVerifier(new NullHostNameVerifier(url.getHost()));
            con.setRequestMethod("HEAD");
            return (con.getResponseCode() == HttpsURLConnection.HTTP_OK);
        }
        catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    protected void startDiscovery() {
        mSubscription = mRxDnssd.browse(mReqType, mDomain)
                .compose(mRxDnssd.resolve())
                .compose(mRxDnssd.queryRecords())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<BonjourService>() {
                    @Override
                    public void call(BonjourService bonjourService) {
                        int itemsCount = mAdapter.getItemCount();
                        if (!bonjourService.isLost()) {
                            // query "/spreedbox-notify/api/v1/static/scripts/spreedbox-notify.js"
                            String url = "";
                            if (bonjourService.getInet4Address() != null) {
                                url = "https://" + bonjourService.getInet4Address().getHostAddress() + "/spreedbox-notify/api/v1/static/scripts/spreedbox-notify.js";
                            }
                            else {
                                url = "https://" + bonjourService.getInet6Address().getHostAddress() + "/spreedbox-notify/api/v1/static/scripts/spreedbox-notify.js";
                            }
                            CheckSpreedbox checkSpreedbox = new CheckSpreedbox();
                            checkSpreedbox.bonjourService = bonjourService;
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                                    checkSpreedbox.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, url);
                                } else {
                                    checkSpreedbox.execute(url);
                                }
                            }
                            catch (RejectedExecutionException e) {
                                e.printStackTrace();
                            }
                        } else {
                            mAdapter.remove(bonjourService);
                        }
                        ServiceBrowserFragment.this.showList(itemsCount);
                        mAdapter.notifyDataSetChanged();
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        Log.e("DNSSD", "Error: ", throwable);
                        ServiceBrowserFragment.this.showError(throwable);
                    }
                });
    }

    protected boolean showList(int itemsBefore){
        if (itemsBefore > 0 && mAdapter.getItemCount() == 0) {
            mRecyclerView.animate().alpha(0.0f).setInterpolator(new AccelerateDecelerateInterpolator()).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mRecyclerView.setVisibility(View.GONE);
                }
            }).start();
            mProgressView.setAlpha(0.0f);
            mProgressView.setVisibility(View.VISIBLE);
            mProgressView.animate().alpha(1.0f).setInterpolator(new AccelerateDecelerateInterpolator()).start();
            return true;
        }
        if (itemsBefore == 0 && mAdapter.getItemCount() > 0) {
            mProgressView.animate().alpha(0.0f).setInterpolator(new AccelerateDecelerateInterpolator()).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mProgressView.setVisibility(View.GONE);
                }
            }).start();
            mRecyclerView.setAlpha(0.0f);
            mRecyclerView.setVisibility(View.VISIBLE);
            mRecyclerView.animate().alpha(1.0f).setInterpolator(new AccelerateDecelerateInterpolator()).start();
            return false;
        }
        return mAdapter.getItemCount() > 0;
    }

    protected void showError(final Throwable e){
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mRecyclerView.animate().alpha(0.0f).setInterpolator(new AccelerateDecelerateInterpolator()).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mRecyclerView.setVisibility(View.GONE);
                    }
                }).start();
                mProgressView.animate().alpha(0.0f).setInterpolator(new AccelerateDecelerateInterpolator()).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mProgressView.setVisibility(View.GONE);
                    }
                }).start();
                mErrorView.setAlpha(0.0f);
                mErrorView.setVisibility(View.VISIBLE);
                mErrorView.animate().alpha(1.0f).setInterpolator(new AccelerateDecelerateInterpolator()).start();

            }
        });
    }

    protected void stopDiscovery() {
        if (mSubscription != null) {
            mSubscription.unsubscribe();
        }
    }

    public interface ServiceListener {
        void onServiceWasSelected(String domain, String regType, BonjourService service);

        void onCancel();
    }
}

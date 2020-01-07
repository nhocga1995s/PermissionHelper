package com.example.permissionhelper.example;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.collection.ArraySet;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.permissionhelper.R;
import com.example.permissionhelper.helper.PermissionHelper;
import com.example.permissionhelper.helper.PermissionUtil;
import com.example.permissionhelper.helper.exception.PermissionNotDefined;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity implements PermissionHelper.RationaleCallback, PermissionHelper.ResultCallback {
    public static final String TAG = MainActivity.class.getSimpleName();
    private static final List<String> PERMISSIONS = Arrays.asList(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BODY_SENSORS,
            Manifest.permission.CAMERA,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO);
    private static final int REQUEST_CODE_1 = 1;
    private static final int REQUEST_CODE_2 = 2;

    private RecyclerView recyclerView;
    private Adapter adapter;
    private PermissionHelper helper;
    private int mRequestCode = REQUEST_CODE_1;
    private PermissionHelper.PermissionPredicate mPermissionPredicate;
    Button mButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        recyclerView = findViewById(R.id.list);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Adapter();
        recyclerView.setAdapter(adapter);
        helper = new PermissionHelper.Builder(this)
                .result(this)
                .rational(this)
                .explain()
                .build();
        mButton = findViewById(R.id.button);
        mButton.setOnClickListener((v) -> {
            try {
                helper.requestPermission(mRequestCode, PERMISSIONS);
            } catch (PermissionNotDefined e) {
                e.printStackTrace();
                Log.e(TAG, e.getMessage());
            }
        });
        findViewById(R.id.button1).setOnClickListener((v) ->
                Toast.makeText(this, "Clicked", Toast.LENGTH_SHORT).show()
        );
        Log.d(TAG, "onCreate");
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(TAG, "onRestart");
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        Log.d(TAG, "onSaveInstanceState");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // check user enable/disable permissions in setting here
        Log.d(TAG, "onActivityResult, code: " + requestCode);
    }

    @Override
    public void rationale(int requestCode, @NonNull PermissionHelper.PermissionPredicate predicate,
                          @NonNull List<String> rationale) {
        mPermissionPredicate = predicate;
        showInfoDialog(rationale, false);
    }

    @Override
    public void onAllGranted(int requestCode, @NonNull List<String> granted) {
        switch (requestCode) {
            case REQUEST_CODE_1:
                adapter.set(createListData(granted, State.GRANTED));
                break;
            case REQUEST_CODE_2:
                break;
        }
    }

    @Override
    public void onAllDenied(int requestCode, @NonNull List<String> denied, @NonNull List<String> deniedForever) {
        switch (requestCode) {
            case REQUEST_CODE_1:
                List<Data> data = createListData(denied, State.DENIED);
                data.addAll(createListData(deniedForever, State.DENIED_FOREVER));
                adapter.set(data);

                showInfoDialog(deniedForever, true);
                break;
            case REQUEST_CODE_2:
                break;
            default:
        }
    }

    @Override
    public void onDenied(int requestCode, @NonNull List<String> denied, @NonNull List<String> granted, @NonNull List<String> deniedForever) {
        switch (requestCode) {
            case REQUEST_CODE_1:
                List<Data> data = createListData(denied, State.DENIED);
                data.addAll(createListData(deniedForever, State.DENIED_FOREVER));
                data.addAll(createListData(granted, State.GRANTED));
                adapter.set(data);

                showInfoDialog(deniedForever, true);
                break;
            case REQUEST_CODE_2:
                break;
            default:
        }
    }

    @NonNull
    List<Data> createListData(@NonNull List<String> permission, @NonNull State state) {
        List<Data> dataList = new ArrayList<>();
        for (String p : permission) {
            p = p.substring(p.lastIndexOf(".") + 1);
            dataList.add(new Data(p, state.name));
        }
        return dataList;
    }

    void showInfoDialog(List<String> permissions, final boolean isOpenSetting) {
        if (permissions.size() == 0) return;
        ArraySet<CharSequence> set = PermissionUtil.getPermissionsGroupName(permissions);
        if (set.size() == 0) return;
        StringBuilder builder = new StringBuilder(!isOpenSetting ?
                "We need these permissions: \n"
                : "We can't request flowing permission again, cause you had choose Never ask again.\n" +
                "Please go to setting and enable it.\n");

        for (CharSequence sequence : set) {
            builder.append("\t\t* ").append(sequence).append("\n");
        }

        AlertDialog alertDialog = new AlertDialog.Builder(this)
                .setMessage(builder.toString())
                .setPositiveButton("OK", (dialog, which) -> {
                    dialog.dismiss();
                    if (isOpenSetting) {
                        PermissionUtil.openAppDetailsSettings(this, 111);
                    } else {
                        if (mPermissionPredicate != null) {
                            mPermissionPredicate.continues(mRequestCode, true);
                            mPermissionPredicate = null;
                        }
                    }
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    dialog.dismiss();
                    if (isOpenSetting && mPermissionPredicate != null) {
                        mPermissionPredicate.continues(mRequestCode, false);
                        mPermissionPredicate = null;
                    }
                })
                .setCancelable(true)
                .create();
        alertDialog.show();
    }

    public static class Adapter extends RecyclerView.Adapter<Adapter.MViewHolder> {
        List<Data> dataSet;

        @NonNull
        @Override
        public MViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new MViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull MViewHolder holder, int position) {
            Data data = dataSet.get(position);
            if (data != null) {
                holder.set(data);
            }
        }

        @Override
        public int getItemCount() {
            return dataSet.size();
        }

        public Adapter() {
            dataSet = new ArrayList<>();
        }

        public void add(@NonNull Data data) {
            dataSet.add(data);
            notifyItemInserted(getItemCount() - 1);
        }

        public void set(@NonNull List<Data> data) {
            dataSet = data;
            notifyDataSetChanged();
        }

        public static class MViewHolder extends RecyclerView.ViewHolder {
            private TextView name;
            private TextView state;

            public MViewHolder(@NonNull View v) {
                super(v);
                name = v.findViewById(R.id.name);
                state = v.findViewById(R.id.state);
            }

            public void set(@NonNull Data data) {
                name.setText(data.name);
                state.setText(data.state);
            }
        }
    }

    public static class Data {
        String name;
        String state;

        public Data(@NonNull String name, @NonNull String state) {
            this.name = name;
            this.state = state;
        }
    }

    public enum State {
        GRANTED("Granted"),
        DENIED("Denied"),
        DENIED_FOREVER("Denied forever");
        String name;

        State(String name) {
            this.name = name;
        }
    }
}

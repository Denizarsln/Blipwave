package dev.blipwave.ui.device;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import dev.blipwave.ble.model.ConnectionState;
import dev.blipwave.ble.model.GattServiceInfo;
import dev.blipwave.data.ConnectionManager;
import dev.blipwave.data.DeviceSession;
import dev.blipwave.data.RssiHistory;
import dev.blipwave.util.LiveDataBridge;

@HiltViewModel
public class DeviceDetailViewModel extends ViewModel {

    private final ConnectionManager connectionManager;
    private final String address;
    @Nullable private final String name;
    private final boolean classic;
    private final DeviceSession session;

    private final LiveData<ConnectionState> state;
    private final LiveData<List<GattServiceInfo>> services;
    private final LiveData<List<RssiHistory.Sample>> rssiHistory;

    @Inject
    public DeviceDetailViewModel(SavedStateHandle savedStateHandle, ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
        String argAddress = savedStateHandle.get(DeviceDetailFragment.ARG_ADDRESS);
        if (argAddress == null) throw new IllegalArgumentException("Cihaz adresi eksik");
        this.address = argAddress;
        this.name = savedStateHandle.get(DeviceDetailFragment.ARG_NAME);
        Boolean classicArg = savedStateHandle.get(DeviceDetailFragment.ARG_CLASSIC);
        this.classic = Boolean.TRUE.equals(classicArg);

        // Ekran açılır açılmaz bağlantı başlar; oturum zaten varsa aynısı kullanılır
        this.session = connectionManager.connect(address, name, classic);
        this.state = LiveDataBridge.of(session.connection().state());
        this.services = LiveDataBridge.of(session.connection().services());
        this.rssiHistory = LiveDataBridge.of(session.rssiHistory());
    }

    public String address() {
        return address;
    }

    @Nullable
    public String displayName() {
        String deviceName = session.connection().getDeviceName();
        return deviceName != null ? deviceName : name;
    }

    public LiveData<ConnectionState> state() { return state; }
    public LiveData<List<GattServiceInfo>> services() { return services; }
    public LiveData<List<RssiHistory.Sample>> rssiHistory() { return rssiHistory; }

    public void connect() {
        connectionManager.connect(address, name, classic);
    }

    public void disconnect() {
        connectionManager.disconnect(address);
    }
}

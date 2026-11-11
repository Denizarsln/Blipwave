package dev.blipwave.ui.connections;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import java.util.Map;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import dev.blipwave.data.ConnectionManager;
import dev.blipwave.data.DeviceSession;
import dev.blipwave.util.LiveDataBridge;

@HiltViewModel
public class ConnectionsViewModel extends ViewModel {

    private final ConnectionManager connectionManager;
    private final LiveData<Map<String, DeviceSession>> sessions;

    @Inject
    public ConnectionsViewModel(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
        this.sessions = LiveDataBridge.of(connectionManager.sessions());
    }

    public LiveData<Map<String, DeviceSession>> sessions() {
        return sessions;
    }

    public void forget(String address) {
        connectionManager.forget(address);
    }
}

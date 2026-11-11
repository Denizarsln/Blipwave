package dev.blipwave.ui.chat;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.snackbar.Snackbar;

import dagger.hilt.android.AndroidEntryPoint;
import dev.blipwave.R;
import dev.blipwave.ble.model.ConnectionState;
import dev.blipwave.data.ByteStats;
import dev.blipwave.data.PayloadCodec;
import dev.blipwave.databinding.FragmentChatBinding;
import dev.blipwave.ui.components.Ui;

@AndroidEntryPoint
public class ChatFragment extends Fragment {

    public static final String ARG_ADDRESS = "address";

    private FragmentChatBinding binding;
    private ChatViewModel viewModel;
    private MessageAdapter adapter;
    private boolean connected;
    private ByteStats lastStats = ByteStats.EMPTY;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentChatBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(ChatViewModel.class);
        Context context = requireContext();

        String name = viewModel.deviceName();
        binding.toolbar.setTitle(name != null ? name : getString(R.string.chat_title));
        binding.toolbar.setNavigationOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_clear) {
                viewModel.clearLog();
                return true;
            }
            return false;
        });

        styleCounter(binding.txCounter, R.drawable.ic_arrow_up, Ui.color(context, R.color.bp_primary));
        styleCounter(binding.rxCounter, R.drawable.ic_arrow_down, Ui.color(context, R.color.bp_tertiary));

        adapter = new MessageAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(context);
        layoutManager.setStackFromEnd(true);
        binding.messageList.setLayoutManager(layoutManager);
        binding.messageList.setAdapter(adapter);

        binding.formatGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            PayloadCodec.Format format = checkedId == R.id.format_hex ? PayloadCodec.Format.HEX : PayloadCodec.Format.TEXT;
            String converted = viewModel.setFormat(format, binding.input.getText().toString());
            binding.input.setText(converted);
            binding.input.setSelection(converted.length());
        });

        binding.input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                viewModel.onInputChanged();
                updateSendEnabled();
            }
        });
        binding.input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                send();
                return true;
            }
            return false;
        });
        binding.sendButton.setOnClickListener(v -> send());
        binding.quickPing.setOnClickListener(v -> viewModel.sendQuick("PING"));
        binding.quickHello.setOnClickListener(v -> viewModel.sendQuick("Hello"));

        viewModel.state().observe(getViewLifecycleOwner(), this::renderState);
        final boolean[] firstLoad = {true};
        viewModel.messages().observe(getViewLifecycleOwner(), messages -> {
            if (firstLoad[0]) {
                adapter.markSeen(messages);
                firstLoad[0] = false;
            }
            adapter.submitList(messages, () -> {
                // Yeni mesaj gelince en alta kay
                if (binding != null && !messages.isEmpty()) {
                    binding.messageList.smoothScrollToPosition(messages.size() - 1);
                }
            });
            binding.emptyState.setVisibility(messages.isEmpty() ? View.VISIBLE : View.GONE);
            binding.toolbar.getMenu().findItem(R.id.action_clear).setEnabled(!messages.isEmpty());
        });
        viewModel.stats().observe(getViewLifecycleOwner(), this::renderStats);
        viewModel.format().observe(getViewLifecycleOwner(), this::renderFormat);
        viewModel.inputError().observe(getViewLifecycleOwner(),
                error -> binding.inputError.setVisibility(Boolean.TRUE.equals(error) ? View.VISIBLE : View.GONE));
        viewModel.snackbar().observe(getViewLifecycleOwner(), event -> {
            Integer message = event.consume();
            if (message != null) Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void send() {
        if (viewModel.send(binding.input.getText().toString())) binding.input.setText("");
    }

    private void renderState(ConnectionState state) {
        connected = state.isConnected();
        binding.statusPill.setState(state);
        Ui.setEnabledWithAlpha(binding.quickPing, connected);
        Ui.setEnabledWithAlpha(binding.quickHello, connected);
        updateSendEnabled();
    }

    private void updateSendEnabled() {
        if (binding == null) return;
        Ui.setEnabledWithAlpha(binding.sendButton, connected && binding.input.length() > 0);
    }

    private void renderFormat(PayloadCodec.Format format) {
        boolean hex = format == PayloadCodec.Format.HEX;
        int checked = hex ? R.id.format_hex : R.id.format_text;
        if (binding.formatGroup.getCheckedButtonId() != checked) binding.formatGroup.check(checked);
        binding.input.setHint(hex ? R.string.chat_hint_hex : R.string.chat_hint_text);
        binding.input.setInputType(hex
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        binding.input.setTypeface(hex ? android.graphics.Typeface.MONOSPACE : android.graphics.Typeface.DEFAULT);
        adapter.setFormat(format);
    }

    private void renderStats(ByteStats stats) {
        bumpIfChanged(binding.txCounter, lastStats.sentBytes != stats.sentBytes);
        bumpIfChanged(binding.rxCounter, lastStats.receivedBytes != stats.receivedBytes);
        lastStats = stats;
        binding.txCounter.setText(getString(R.string.chat_tx) + "  " + getString(R.string.chat_bytes, stats.sentBytes));
        binding.rxCounter.setText(getString(R.string.chat_rx) + "  " + getString(R.string.chat_bytes, stats.receivedBytes));
    }

    /** Sayaç değişince rakamlar hafifçe yukarıdan "düşer". */
    private void bumpIfChanged(TextView view, boolean changed) {
        if (!changed) return;
        view.setTranslationY(-Ui.dp(requireContext(), 6));
        view.setAlpha(0.4f);
        view.animate().translationY(0f).alpha(1f).setDuration(220L).start();
    }

    private void styleCounter(TextView view, @DrawableRes int icon, @ColorInt int color) {
        Ui.styleBadge(view, color);
        view.setTextColor(Ui.color(requireContext(), R.color.bp_on_background));
        Drawable drawable = AppCompatResources.getDrawable(requireContext(), icon);
        if (drawable != null) {
            drawable = drawable.mutate();
            drawable.setTint(color);
            int size = (int) Ui.dp(requireContext(), 14);
            drawable.setBounds(0, 0, size, size);
            view.setCompoundDrawablesRelative(drawable, null, null, null);
        }
    }
}

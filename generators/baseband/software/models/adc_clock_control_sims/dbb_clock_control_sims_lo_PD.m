f_if = 2e6; % IF frequency in Hz
lo_divider = 8;
f_lo = 2.4e9/lo_divider; % LO frequency in Hz
fs_adc = 32e6 + 2.5e4; % ADC sampling frequency in Hz. 1.28e3 is 40 ppm.
fs = fs_adc * 50; % Oversampled for smoothness
duration = 10e-3; % Duration in seconds
adc_res = 8; % ADC resolution in bits
t = 0:(1/fs):duration;
sig = sin(2*pi*f_if*t);

% Plot IF signal
% figure;
% plot(t(1:1e3), y(1:1e3));
% xlabel('Time (s)');
% ylabel('Amplitude');
% title('2 MHz Sin Wave');
% grid on;

adc_samples = resample(sig, fs_adc, fs);
% length(adc_samples)
% length(sig)

adc_samples = adc_samples + 1;
adc_samples = adc_samples * 2^(adc_res - 1);
adc_samples = round(adc_samples);

% Plot ADC samples
% figure;
% plot(adc_samples(1:1e3));
% xlabel('Sample');
% ylabel('Amplitude');
% title('ADC Samples');
% grid on;

cutoff = 2^(adc_res - 1);
% if_zero_crossings_counter = 0;
% prev_sample = 0;
% for i = 1:length(adc_samples)
%     curr_sample = adc_samples(i);
%     if curr_sample >= cutoff && prev_sample < cutoff
%         if_zero_crossings_counter = if_zero_crossings_counter + 1;
%     end
%     prev_sample = curr_sample;
% end

% fprintf('Number of Zero Crossings: %d\n', if_zero_crossings_counter);

% Simulating sequential block clocked at fs_adc
adc_tick_counter_local = 0;
adc_tick_counter_global = 0;

% Assume clock drifts +- 40 ppm every 64 us. Let's try to figure out new clock frequency within 64 us.
num_adc_ticks_threshold = 2048; % Corresponds to 64 us.
num_adc_ticks_clock_change = 2048; % Corresponds to 64 us.
calc_adc_clock_freq_buffer = [];
actual_adc_clock_freq_buffer = [fs_adc];
error_arr = [];

dt = 64e-6;
drift_min = -1.28e3;
drift_max = 1.28e3;
Kp = 1/1.28e3 * 0.5;
Ki = 5 * dt;
offset = 1;
past_error = 0;
error = 0;
accumulated_error = 0;
control_word = 32;
delta_control_word = 0;
controller_active = true;
p_only = 0;
i = 0;

fprintf('Initial ADC clock frequency: %f Hz\n', fs_adc);
fprintf('Initial Control Word: %d\n', control_word);
fprintf('There Is An LO/32 Crossing (Negative Edge) Every 32 LO/32 Samples\n\n');

while true
    if adc_tick_counter_global == length(adc_samples)
        break
    end

    if adc_tick_counter_local == num_adc_ticks_threshold % Roughly 40 us.
        [counter] = lo_counter(fs_adc, num_adc_ticks_threshold, f_lo);
        fprintf('Num LO/%d Crossings Detected In %d ADC Ticks: %d\n', lo_divider, num_adc_ticks_threshold, counter);
        
        calc_adc_clock_freq = f_lo/(counter/num_adc_ticks_threshold);
        fprintf('Calculated Current ADC Clock Frequency: %f Hz\n', calc_adc_clock_freq);

        error = 32e6 - calc_adc_clock_freq;
        fprintf('Error: %f Hz\n', error);
        error_dot = (error - past_error) / dt;
        accumulated_error = accumulated_error + error;
        fprintf('Accumulated Error: %.10f Hz\n', accumulated_error);
        past_error = error;

        p_term = Kp * error;
        if (p_term > 0)
            p_term = floor(p_term);
        else
            p_term = ceil(p_term);
        end

        i_term = accumulated_error * Ki;
        if (i_term > 0)
            i_term = floor(i_term);
        else
            i_term = ceil(i_term);
        end

        prev_control_word = control_word;
        control_word = max(0, p_only * prev_control_word + p_term + i_term);
        control_word = min(255, control_word);
        delta_control_word = control_word - prev_control_word;

        fprintf('P Term: %f\n', p_term);
        fprintf('I Term: %f\n', i_term);
        fprintf('Control Word: %f\n', control_word);
        fprintf('Previous Control Word: %f\n', prev_control_word);
        fs_adc = fs_adc + calc_freq_offset(prev_control_word, delta_control_word, controller_active);
        fprintf('ADC Clock Frequency Adjusted To: %f Hz\n', fs_adc);

        error_arr(end+1) = error;
        calc_adc_clock_freq_buffer(end+1) = calc_adc_clock_freq;
    end
    
    if adc_tick_counter_local == num_adc_ticks_clock_change % Roughly 64 us.
        prev_fs_adc = fs_adc;
        if (i < 1000000) 
            fs_adc = fs_adc + drift_min+rand(1)*(drift_max-drift_min);
        else
            fs_adc = fs_adc;
        end
        actual_adc_clock_freq_buffer(end+1) = fs_adc;

        fprintf('ADC Clock Frequency Drifted To: %f Hz\n\n', fs_adc);
        i = i + 1;

        % % Simulating clock frequency change
        % old_adc_samples = adc_samples(1:(adc_tick_counter_global+1));
        % new_adc_samples = resample(adc_samples((adc_tick_counter_global+2):end), fs_adc, prev_fs_adc);
        
        % fprintf('Old ADC Samples Length: %d\n', length(old_adc_samples));
        % fprintf('New ADC Samples Length Before Resampling: %d\n', length(adc_samples((adc_tick_counter_global+2):end)))
        % fprintf('New ADC Samples Length After Resampling: %d\n', length(new_adc_samples));

        % adc_samples = [old_adc_samples new_adc_samples];
        % fprintf('Full ADC Samples Length: %d\n', length(adc_samples));

        adc_tick_counter_local = 0;
    end

    adc_tick_counter_local = adc_tick_counter_local + 1;
    adc_tick_counter_global = adc_tick_counter_global + 1;
end

% figure;
% plot(0:0.512e3:32e3, calc_adc_clock_freq_buffer);
% yline(32e6 + 1280, '--', '32 + 40ppm (MHz)', 'Color', "#77AC30");
% yline(32e6 - 1280, '--', '32 - 40ppm (MHz)', 'Color', "#77AC30");
% y1 = yline(32e6, '--', '32 (MHz)', 'Color', 'Red');
% xlabel('Num ADC Ticks Threshold');
% ylabel('Calculated ADC Clock Frequency (Hz)');
% title('PD Control | Kp=0.4, Kd=0.3, Offset=-40KHz');
% grid on;

figure;
% stairs(linspace(64, duration * 1e6 - 64, length(calc_adc_clock_freq_buffer)), calc_adc_clock_freq_buffer, 'r');
% hold on;
stairs(linspace(0, duration * 1e6, length(actual_adc_clock_freq_buffer)), actual_adc_clock_freq_buffer, 'b');
xlabel('Time (us)');
ylabel('Frequency (Hz)');
title(['Calculated and Actual ADC Clock Frequencies vs Time (us) for LO/', num2str(lo_divider), ' - Clock Change Every ', num2str(num_adc_ticks_clock_change/32),' and Calculation Time ', num2str(num_adc_ticks_threshold/32), 'us']);
yline(32e6 + 1920, '--', '32 + 60ppm (MHz)', 'Color', "#77AC30");
yline(32e6 - 1920, '--', '32 - 60ppm (MHz)', 'Color', "#77AC30");
legend('Calculated', 'Actual');

grid on;

function [adc_clk_freq_offset] = calc_freq_offset(curr_control_word, delta_control_word, controller_active)
    if ~controller_active
        adc_clk_freq_offset = 0;
        return;
    end
    res = 0;
    if (curr_control_word >= 0 && curr_control_word < 32) 
        res = 1.28e3;
    elseif (curr_control_word >= 32 && curr_control_word < 64)
        res = 1.14e3;
    elseif (curr_control_word >= 64 && curr_control_word < 96)
        res = 1.00e3;
    elseif (curr_control_word >= 96 && curr_control_word < 128)
        res = 0.86e3;
    elseif (curr_control_word >= 128 && curr_control_word < 160)
        res = 0.72e3;
    elseif (curr_control_word >= 160 && curr_control_word < 192)
        res = 0.58e3;
    elseif (curr_control_word >= 192 && curr_control_word < 224)
        res = 0.44e3;
    elseif (curr_control_word >= 224 && curr_control_word < 256)
        res = 0.30e3;
    end
    adc_clk_freq_offset = delta_control_word * res;
end

f_if = 2e6; % IF frequency in Hz
lo_divider = 8;
f_lo = 2.4e9/lo_divider; % LO frequency in Hz
fs_adc = 32e6; % ADC sampling frequency in Hz. 1.28e3 is 40 ppm.
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

% Assume clock drifts +- 40 ppm every 32 us. Let's try to figure out new clock frequency within 16 us.
num_adc_ticks_threshold = 2048; % Corresponds to 64 us.
num_adc_ticks_clock_change = 2048; % Corresponds to 64 us.
calc_adc_clock_freq_buffer = [];
actual_adc_clock_freq_buffer = [fs_adc];

fprintf('Initial ADC clock frequency: %f Hz\n', fs_adc);
fprintf('There Is An LO/32 Crossing (Negative Edge) Every 32 LO/32 Samples\n\n');

while true
    if adc_tick_counter_global == length(adc_samples)
        break
    end

    if adc_tick_counter_local == num_adc_ticks_threshold % Roughly 64 us.
        [counter] = lo_counter(fs_adc, num_adc_ticks_threshold, f_lo);
        fprintf('Num LO/%d Crossings Detected In %d ADC Ticks: %d\n', lo_divider, num_adc_ticks_threshold, counter);
        
        calc_adc_clock_freq = f_lo/(counter/num_adc_ticks_threshold);
        calc_adc_clock_freq_buffer(end+1) = calc_adc_clock_freq;

        fprintf('Calculated Current ADC Clock Frequency: %f Hz\n\n', calc_adc_clock_freq);
    end
    
    if adc_tick_counter_local == num_adc_ticks_clock_change && adc_tick_counter_global < fs_adc * duration - num_adc_ticks_threshold
        prev_fs_adc = fs_adc;
        fs_adc = fs_adc + 2.56e3 * (randi(2) - 1.5);
        actual_adc_clock_freq_buffer(end+1) = fs_adc;

        fprintf('ADC Clock Frequency Changed To: %f Hz\n', fs_adc);

        %Simulating clock frequency change
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

figure;
stairs(linspace(64, duration * 1e6, length(calc_adc_clock_freq_buffer)), calc_adc_clock_freq_buffer, 'r');
hold on;
stairs(linspace(0, duration * 1e6 - 64, length(actual_adc_clock_freq_buffer)), actual_adc_clock_freq_buffer, 'b');
xlabel('Time (us)');
ylabel('Frequency (Hz)');
title(['Calculated and Actual ADC Clock Frequencies vs Time (us) for LO/', num2str(lo_divider), ' - Clock Change Every ', num2str(num_adc_ticks_clock_change/32),' and Calculation Time ', num2str(num_adc_ticks_threshold/32), 'us']);
legend('Calculated', 'Actual');
grid on;

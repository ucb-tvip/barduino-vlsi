f_if = 2e6; % IF frequency in Hz
fs_adc = 32e6; % ADC sampling frequency in Hz. 1.28e3 is 40 ppm.
fs = fs_adc * 50; % Oversampled for smoothness
duration = 1e-3; % Duration in seconds
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
prev_sample = cutoff;
prev_fs_adc = fs_adc;
adc_tick_counter_local = 0;
adc_tick_counter_global = 0;

% Assume clock drifts +- 40 ppm every 32 us. Let's try to figure out new clock frequency within 16 us.
num_zero_crossings_threshold = 32; % Corresponds to 16 us.
num_zero_crossings_local = 0;
num_zero_crossings_global = 0;

fprintf('Initial ADC clock frequency: %f Hz\n', fs_adc);
fprintf('Initial Number of ADC Samples: %d\n', length(adc_samples));

while true
    if adc_tick_counter_global == length(adc_samples)
        break
    end

    curr_sample = adc_samples(adc_tick_counter_global + 1);

    if curr_sample >= cutoff && prev_sample < cutoff % Zero crossing detected
        num_zero_crossings_local = num_zero_crossings_local + 1;
        num_zero_crossings_global = num_zero_crossings_global + 1;
        % fprintf('Zero crossing detected at tick %d\n', adc_tick_counter_global);

        if num_zero_crossings_local == num_zero_crossings_threshold
            fprintf('Num ADC Ticks To Detect %d Zero Crossings (%d us): %d\n', num_zero_crossings_threshold, num_zero_crossings_threshold/2, adc_tick_counter_local);
            fprintf('Calculated Current ADC clock frequency: %f Hz\n\n', adc_tick_counter_local/num_zero_crossings_threshold * f_if);
        end

        if (mod(num_zero_crossings_global, 64) == 0) % Happens every 32 us.
            prev_fs_adc = fs_adc;
            fs_adc = fs_adc + 2.56e3 * (randi(2) - 1.5);
            fprintf('ADC Clock Frequency Changed To: %f Hz\n', fs_adc);

            %Simulating clock frequency change
            old_adc_samples = adc_samples(1:(adc_tick_counter_global+1));
            new_adc_samples = resample(adc_samples((adc_tick_counter_global+2):end), fs_adc, prev_fs_adc);
            
            fprintf('Old ADC Samples Length: %d\n', length(old_adc_samples));
            fprintf('New ADC Samples Length Before Resampling: %d\n', length(adc_samples((adc_tick_counter_global+2):end)))
            fprintf('New ADC Samples Length After Resampling: %d\n', length(new_adc_samples));

            adc_samples = [old_adc_samples new_adc_samples];
            fprintf('Full ADC Samples Length: %d\n', length(adc_samples));

            num_zero_crossings_local = 0;
            adc_tick_counter_local = 0;
        end
    end

    prev_sample = curr_sample;
    adc_tick_counter_local = adc_tick_counter_local + 1;
    adc_tick_counter_global = adc_tick_counter_global + 1;
end

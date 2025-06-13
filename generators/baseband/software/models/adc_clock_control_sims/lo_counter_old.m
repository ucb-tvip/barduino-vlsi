function [num_transitions, num_lo_samples] = lo_counter_old(adc_clock_freq, num_adc_clock_cycles, f_lo)
    dutyCycle = 50; % Duty cycle in %

    duration = 1e-3; % Duration in seconds
    fs = f_lo * 32; % LO Sampling frequency in Hz
    t = 0:(1/fs):duration;
    % fprintf('Number of samples: %d\n', length(t));

    lo = square(2*pi*f_lo*t, dutyCycle);
    lo = (lo + 1)/2;

    % plot(t(1:240), lo(1:240));
    % xlabel('Time (s)');
    % ylabel('Amplitude');
    % title('Simulated Clock Signal');
    % grid on;

    period = (1/adc_clock_freq) * num_adc_clock_cycles;
    num_lo_samples = round(period * fs);
    num_transitions = 0; % Counter for number of 0 to 1 transitions.

    % fprintf('Number of samples in %d period(s) of 1/adc_clock_freq: %d\n', num_adc_clock_cycles, num_lo_samples);

    for i = 1:num_lo_samples
        if lo(i) == 1 && lo(i+1) == 0
            num_transitions = num_transitions + 1;
        end
    end

    % fprintf('Number of LO 1 to 0 transitions in %d cycles of adc_clock_freq: %d\n', num_adc_clock_cycles, num_transitions);
end

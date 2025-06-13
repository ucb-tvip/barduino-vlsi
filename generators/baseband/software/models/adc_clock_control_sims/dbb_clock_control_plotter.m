num_iter_p_only_mode_0 = 156;
duration_us_p_only_mode_0 = 64e-6;
test_duration_us_p_only_mode_0 = duration_us_p_only_mode_0 * num_iter_p_only_mode_0;

lo_divider_p_only_mode_0 = 8;
num_adc_ticks_threshold_p_only_mode_0 = 2048;
num_adc_ticks_clock_change_p_only_mode_0 = 2048;

adc_clock_freq_arr_filepath_p_only_mode_0 = "../../../src/test/scala/modem/data/adc_clock_freq_arr_p_only_mode_0.csv";
adc_clock_freq_arr_filepath_p_only_mode_0_no_control = "../../../src/test/scala/modem/data/adc_clock_freq_arr_p_only_mode_0_no_control.csv";
calc_adc_clock_freq_arr_filepath_p_only_mode_0 = "../../../src/test/scala/modem/data/calc_adc_clock_freq_arr_p_only_mode_0.csv";

actual_adc_clock_freq_buffer_p_only_mode_0 = csvread(adc_clock_freq_arr_filepath_p_only_mode_0);
actual_adc_clock_freq_buffer_p_only_mode_0_no_control = csvread(adc_clock_freq_arr_filepath_p_only_mode_0_no_control);
calc_adc_clock_freq_buffer_p_only_mode_0 = csvread(calc_adc_clock_freq_arr_filepath_p_only_mode_0);

figure;
% stairs(linspace(64, test_duration_us * 1e6, length(calc_adc_clock_freq_buffer_p_only_mode_0)), calc_adc_clock_freq_buffer_p_only_mode_0, 'r');
% hold on;
stairs(linspace(0, test_duration_us_p_only_mode_0 * 1e6 - 64, length(actual_adc_clock_freq_buffer_p_only_mode_0_no_control)), actual_adc_clock_freq_buffer_p_only_mode_0_no_control, 'g');
hold on;
stairs(linspace(0, test_duration_us_p_only_mode_0 * 1e6 - 64, length(actual_adc_clock_freq_buffer_p_only_mode_0)), actual_adc_clock_freq_buffer_p_only_mode_0, 'b');
xlabel('Time (us)');
ylabel('Frequency (Hz)');
title(['(P Controller) Analog Oscillator Frequency vs Time (us) for LO/', num2str(lo_divider_p_only_mode_0), ' - Calculation Time ', num2str(num_adc_ticks_threshold_p_only_mode_0/32), 'us']);
yline(32e6 + 1920, '--', '32 + 60ppm (MHz)', 'Color', "#FF0000");
yline(32e6 - 1920, '--', '32 - 60ppm (MHz)', 'Color', "#FF0000");
legend('ADC Clock Frequency - Controller Off', 'ADC Clock Frequency - Controller On');
grid on;

num_iter_p_and_i_mode_0 = 156;
duration_us_p_and_i_mode_0 = 64e-6;
test_duration_us_p_and_i_mode_0 = duration_us_p_and_i_mode_0 * num_iter_p_and_i_mode_0;

lo_divider_p_and_i_mode_0 = 8;
num_adc_ticks_threshold_p_and_i_mode_0 = 2048;
num_adc_ticks_clock_change_p_and_i_mode_0 = 2048;

adc_clock_freq_arr_filepath_p_and_i_mode_0 = "../../../src/test/scala/modem/data/adc_clock_freq_arr_p_and_i_mode_0.csv";
adc_clock_freq_arr_filepath_p_and_i_mode_0_no_control = "../../../src/test/scala/modem/data/adc_clock_freq_arr_p_and_i_mode_0_no_control.csv";
calc_adc_clock_freq_arr_filepath_p_and_i_mode_0 = "../../../src/test/scala/modem/data/calc_adc_clock_freq_arr_p_and_i_mode_0.csv";

actual_adc_clock_freq_buffer_p_and_i_mode_0 = csvread(adc_clock_freq_arr_filepath_p_and_i_mode_0);
actual_adc_clock_freq_buffer_p_and_i_mode_0_no_control = csvread(adc_clock_freq_arr_filepath_p_and_i_mode_0_no_control);
calc_adc_clock_freq_buffer_p_and_i_mode_0 = csvread(calc_adc_clock_freq_arr_filepath_p_and_i_mode_0);

figure;
% stairs(linspace(64, test_duration_us * 1e6, length(calc_adc_clock_freq_buffer_p_and_i_mode_0)), calc_adc_clock_freq_buffer_p_and_i_mode_0, 'r');
% hold on;
stairs(linspace(0, test_duration_us_p_and_i_mode_0 * 1e6 - 64, length(actual_adc_clock_freq_buffer_p_and_i_mode_0_no_control)), actual_adc_clock_freq_buffer_p_and_i_mode_0_no_control, 'g');
hold on;
stairs(linspace(0, test_duration_us_p_and_i_mode_0 * 1e6 - 64, length(actual_adc_clock_freq_buffer_p_and_i_mode_0)), actual_adc_clock_freq_buffer_p_and_i_mode_0, 'b');
xlabel('Time (us)');
ylabel('Frequency (Hz)');
title(['(PI Controller) Analog Oscillator Frequency vs Time (us) for LO/', num2str(lo_divider_p_and_i_mode_0), ' - Calculation Time ', num2str(num_adc_ticks_threshold_p_and_i_mode_0/32), 'us']);
yline(32e6 + 1920, '--', '32 + 60ppm (MHz)', 'Color', "#FF0000");
yline(32e6 - 1920, '--', '32 - 60ppm (MHz)', 'Color', "#FF0000");
legend('ADC Clock Frequency - Controller Off', 'ADC Clock Frequency - Controller On');
grid on;

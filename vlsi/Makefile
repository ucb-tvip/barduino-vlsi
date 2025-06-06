#########################################################################################
# vlsi makefile
#########################################################################################

#########################################################################################
# general path variables
#########################################################################################
base_dir=$(abspath ..)
vlsi_dir=$(abspath .)
sim_dir=$(abspath .)

#########################################################################################
# include shared variables
#########################################################################################
include $(vlsi_dir)/tutorial.mk
include $(base_dir)/variables.mk

#########################################################################################
# vlsi types and rules
#########################################################################################
sim_name           ?= vcs # needed for GenerateSimFiles, but is unused
tech_name          ?= sky130
tech_dir           ?= $(if $(filter $(tech_name),sky130 asap7 nangate45),\
                        $(shell python3 -c "import os, hammer.technology.$(tech_name);\
						print(os.path.dirname(hammer.technology.$(tech_name).__file__))"),\
                        $(vlsi_dir)/hammer-$(tech_name)-plugin/hammer/$(tech_name))
SMEMS_COMP         ?= $(tech_dir)/sram-compiler.json
SMEMS_CACHE        ?= $(tech_dir)/sram-cache.json
SMEMS_HAMMER       ?= $(build_dir)/$(long_name).mems.hammer.json

IS_TOP_RUN ?= 1

ifdef USE_SRAM_COMPILER
	TOP_MACROCOMPILER_MODE ?= -l $(SMEMS_COMP) --use-compiler -hir $(SMEMS_HAMMER) --mode strict
else
	TOP_MACROCOMPILER_MODE ?= -l $(SMEMS_CACHE) -hir $(SMEMS_HAMMER) --mode strict
endif

HOSTNAME := $(shell hostname)

# tech and tool paths will change, so use different configs- bwrc and inst[ructional] are common environments
ifeq ($(findstring bwrc, $(HOSTNAME)),bwrc)
	PLATFORM  ?= -bwrc
else ifeq ($(findstring eda, $(HOSTNAME)),eda)
	PLATFORM  ?= -inst
endif

ENV_YML            ?= $(vlsi_dir)/env.yml
TECH_CONF          ?= tech-$(tech_name)$(PLATFORM).yml
TOOLS_CONF         ?= tools$(PLATFORM).yml
CHIP_CONF         ?= design-ofo.yml
INPUT_CONFS        ?= $(TOOLS_CONF) $(TECH_CONF) $(CHIP_CONF)
PIN_MAP            ?= pin-map-ofo.yml

ifeq ($(IS_TOP_RUN),1)
	HAMMER_EXEC        ?= ./hammer-driver 
else
	HAMMER_EXEC        ?= ./example-vlsi-sky130
endif

# $(if $(filter $(tech_name),sky130),\
	# ./example-vlsi-sky130,\
	# ./example-vlsi)
VLSI_TOP           ?= $(TOP)
VLSI_MODEL_DUT_NAME ?= chiptop0
# If overriding, this should be relative to $(vlsi_dir)
VLSI_OBJ_DIR       ?= build
export USE_CHISEL6        = 1
ifneq ($(CUSTOM_VLOG),)
	OBJ_DIR          ?= $(vlsi_dir)/$(VLSI_OBJ_DIR)/$(VLSI_TOP)
else
	OBJ_DIR          ?= $(vlsi_dir)/$(VLSI_OBJ_DIR)/$(long_name)-$(TOP)
endif

#########################################################################################
# general rules
#########################################################################################
.PHONY: default all
default: all

all: drc lvs

#########################################################################################
# import other necessary rules and variables
#########################################################################################
include $(base_dir)/common.mk

#########################################################################################
# process RTL
#########################################################################################
VLSI_RTL = $(build_dir)/syn.f

ifneq ($(CUSTOM_VLOG), )
	RTL_DEPS = $(CUSTOM_VLOG)
else
	RTL_DEPS = $(TOP_MODS_FILELIST) $(TOP_SMEMS_FILE) $(EXT_FILELISTS)
endif

$(VLSI_RTL): $(RTL_DEPS)
ifneq ($(CUSTOM_VLOG), )
	> $(VLSI_RTL)
	$(foreach file,$^,echo $(file) >> $(VLSI_RTL))
else
	cat $(TOP_MODS_FILELIST) | sort -u > $(VLSI_RTL)
	echo $(TOP_SMEMS_FILE) >> $(VLSI_RTL)
ifneq ($(EXT_FILELISTS),)
	cat $(EXT_FILELISTS) >> $(VLSI_RTL)
endif
endif

#########################################################################################
# srams
#########################################################################################
SRAM_GENERATOR_CONF = $(OBJ_DIR)/sram_generator-input.yml
SRAM_CONF=$(OBJ_DIR)/sram_generator-output.json

## SRAM Generator
.PHONY: sram_generator srams
srams: sram_generator
sram_generator: $(SRAM_CONF)

# This should be built alongside $(TOP_SMEMS_FILE)
$(SMEMS_HAMMER): $(TOP_SMEMS_FILE)

$(SRAM_GENERATOR_CONF): $(SMEMS_HAMMER)
	mkdir -p $(dir $@)
	echo "vlsi.inputs.sram_parameters: '$(SMEMS_HAMMER)'" > $@
	echo "vlsi.inputs.sram_parameters_meta: [\"transclude\", \"json2list\"]" >> $@

$(SRAM_CONF): $(SRAM_GENERATOR_CONF)
	cd $(vlsi_dir) && $(HAMMER_EXEC) -e $(ENV_YML) $(foreach x,$(INPUT_CONFS) $(SRAM_GENERATOR_CONF), -p $(x)) --obj_dir $(build_dir) sram_generator
	cd $(vlsi_dir) && cp output.json $@

#########################################################################################
# synthesis input configuration
#########################################################################################
SYN_CONF = $(OBJ_DIR)/inputs.yml

GENERATED_CONFS = $(SYN_CONF) 

ifeq ($(IS_TOP_RUN),1)
	# only use ios if non commercial
	GENERATED_CONFS = $(SYN_CONF) $(IO_FILE_CONF)
endif

ifeq ($(CUSTOM_VLOG), )
	GENERATED_CONFS += $(SRAM_CONF)
endif

$(SYN_CONF): $(VLSI_RTL)
	mkdir -p $(dir $@)
	echo "sim.inputs:" > $@
	echo "  input_files:" >> $@
	for x in $$(cat $(VLSI_RTL)); do \
		echo '    - "'$$x'"' >> $@; \
		done
	echo "  input_files_meta: 'append'" >> $@
	echo "synthesis.inputs:" >> $@
	echo "  top_module: $(VLSI_TOP)" >> $@
	echo "  input_files:" >> $@
	for x in $$(cat $(VLSI_RTL)); do \
		echo '    - "'$$x'"' >> $@; \
		done

#########################################################################################
# simulation and power input configuration
#########################################################################################
include $(base_dir)/sims/vcs/vcs.mk

SIM_FILE_REQS += \
		 $(ROCKETCHIP_RSRCS_DIR)/vsrc/TestDriver.v

# copy files but ignore *.h files in *.f since vcs has +incdir+$(build_dir)
$(sim_files): $(SIM_FILE_REQS) $(ALL_MODS_FILELIST) | $(build_dir)
	cp -f $(SIM_FILE_REQS) $(build_dir)
	$(foreach file,\
		$(SIM_FILE_REQS),\
		$(if $(filter %.h,$(file)),\
		,\
		echo "$(addprefix $(build_dir)/, $(notdir $(file)))" >> $@;))

include $(vlsi_dir)/sim.mk

include $(vlsi_dir)/power.mk

#########################################################################################
# io / pin map
#########################################################################################
IO_FILE_CONF = $(OBJ_DIR)/io_map-input.yml
IO_FILE = $(OBJ_DIR)/io_map.io

$(IO_FILE): scripts/gen-io-file.py $(PIN_MAP) $(build_dir)/$(long_name).sky130io.json
	$< $(PIN_MAP) -d $(build_dir)/$(long_name).sky130io.json -o $@

$(IO_FILE_CONF): $(IO_FILE)
	mkdir -p $(dir $@)
	echo "technology.sky130.io_file: '$(notdir $(IO_FILE))'" >> $@
	echo "technology.sky130.io_file_meta: \"prependlocal\"">> $@

#########################################################################################
# AUTO BUILD FLOW
#########################################################################################

.PHONY: buildfile
buildfile: $(OBJ_DIR)/hammer.d
# Tip: Set HAMMER_D_DEPS to an empty string to avoid unnecessary RTL rebuilds
# TODO: make this dependency smarter so that we don't need this at all
HAMMER_D_DEPS ?= $(GENERATED_CONFS)

$(OBJ_DIR)/hammer.d: $(HAMMER_D_DEPS)
	$(HAMMER_EXEC) -e $(ENV_YML) $(foreach x,$(INPUT_CONFS) $(GENERATED_CONFS), -p $(x)) --obj_dir $(OBJ_DIR) build

-include $(OBJ_DIR)/hammer.d

#########################################################################################
# general cleanup rule
#########################################################################################
.PHONY: clean
clean:
	rm -rf $(VLSI_OBJ_DIR) hammer-vlsi*.log __pycache__ output.json $(GENERATED_CONFS) $(CLASSPATH_CACHE) $(gen_dir) $(SIM_CONF) $(SIM_DEBUG_CONF) $(SIM_TIMING_CONF) $(POWER_CONF)

#########################################################################################
# OFOT running VLSI flag
#########################################################################################

# Define a flag to indicate the VLSI (non-sim) Makefile is being run
RUN_TYPE_FLAG =pd
# Export the flag so it can be accessed outside the Makefile
export RUN_TYPE_FLAG

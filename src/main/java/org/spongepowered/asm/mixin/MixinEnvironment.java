/*
 * This file is part of Sponge, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered.org <http://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.asm.mixin;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.core.helpers.Booleans;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.transformer.MixinTransformer;
import org.spongepowered.asm.util.PrettyPrinter;

import com.google.common.collect.ImmutableList;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;


/**
 * The mixin environment manages global state information for the mixin
 * subsystem.
 */
public class MixinEnvironment {
    
    /**
     * Environment phase, deliberately not implemented as an enum
     */
    public static class Phase {
        
        /**
         * Not initialised phase 
         */
        static final Phase NOT_INITIALISED = new Phase(-1, "NOT_INITIALISED");
        
        /**
         * "Pre initialisation" phase, everything before the tweak system begins
         * to load the game
         */
        public static final Phase PREINIT = new Phase(0, "PREINIT");
        
        /**
         * "Default" phase, during runtime
         */
        public static final Phase DEFAULT = new Phase(1, "DEFAULT");
        
        /**
         * All phases
         */
        static final List<Phase> phases = ImmutableList.of(
            Phase.PREINIT,
            Phase.DEFAULT
        );
        
        /**
         * Phase ordinal
         */
        final int ordinal;
        
        /**
         * Phase name
         */
        final String name;
        
        private Phase(int ordinal, String name) {
            this.ordinal = ordinal;
            this.name = name;
        }
        
        @Override
        public String toString() {
            return this.name;
        }
    }
    
    /**
     * Represents a "side", client or dedicated server
     */
    public static enum Side {
        
        /**
         * The environment was unable to determine current side
         */
        UNKNOWN {
            @Override
            protected boolean detect() {
                return false;
            }
        },
        
        /**
         * Client-side environment 
         */
        CLIENT {
            @Override
            protected boolean detect() {
                String sideName = this.getSideName();
                return "CLIENT".equals(sideName);
            }
        },
        
        /**
         * (Dedicated) Server-side environment 
         */
        SERVER {
            @Override
            protected boolean detect() {
                String sideName = this.getSideName();
                return "SERVER".equals(sideName) || "DEDICATEDSERVER".equals(sideName);
            }
        };
        
        protected abstract boolean detect();

        protected final String getSideName() {
            String name = this.getSideName("net.minecraftforge.fml.relauncher.FMLLaunchHandler", "side");
            if (name != null) {
                return name;
            }
            
            name = this.getSideName("cpw.mods.fml.relauncher.FMLLaunchHandler", "side");
            if (name != null) {
                return name;
            }
            
            name = this.getSideName("com.mumfrey.liteloader.core.LiteLoader", "getEnvironmentType");
            if (name != null) {
                return name;
            }
            
            return "UNKNOWN";
        }

        private String getSideName(String className, String methodName) {
            try {
                Class<?> clazz = Class.forName(className, false, Launch.classLoader);
                Method method = clazz.getDeclaredMethod(methodName);
                return ((Enum<?>)method.invoke(null)).name();
            } catch (Exception ex) {
                return null;
            }
        }
    }
    
    /**
     * Mixin options
     */
    public static enum Option {
        
        /**
         * Enable all debugging options
         */
        DEBUG_ALL("debug"),
        
        /**
         * Enable post-mixin class export. This causes all classes to be written
         * to the .mixin.out directory within the runtime directory
         * <em>after</em> mixins are applied, for debugging purposes. 
         */
        DEBUG_EXPORT(Option.DEBUG_ALL, "export"),
        
        /**
         * Run the CheckClassAdapter on all classes after mixins are applied 
         */
        DEBUG_VERIFY(Option.DEBUG_ALL, "verify"),
        
        /**
         * Enable verbose mixin logging (elevates all DEBUG level messages to
         * INFO level) 
         */
        DEBUG_VERBOSE(Option.DEBUG_ALL, "verbose"),
        
        /**
         * Dumps the bytecode for the target class to disk when mixin
         * application fails
         */
        DUMP_TARGET_ON_FAILURE("dumpTargetOnFailure"),
        
        /**
         * Enable all checks 
         */
        CHECK_ALL("checks"),
        
        /**
         * Checks that all declared interface methods are implemented on a class
         * after mixin application.
         */
        CHECK_IMPLEMENTS(Option.CHECK_ALL, "interfaces");
        
        /**
         * Prefix for mixin options
         */
        private static final String PREFIX = "mixin";
        
        /**
         * Parent option to this option, if non-null then this option is enabled
         * if 
         */
        final Option parent;
        
        /**
         * Java property name
         */
        final String property;

        private Option(String property) {
            this(null, property);
        }
        
        private Option(Option parent, String property) {
            this.parent = parent;
            this.property = (parent != null ? parent.property : Option.PREFIX) + "." + property;
        }
        
        public Option getParent() {
            return this.parent;
        }
        
        public String getProperty() {
            return this.property;
        }
        
        protected boolean getValue() {
            return Booleans.parseBoolean(System.getProperty(this.property), false)
                    || (this.parent != null && this.parent.getValue());
        }
    }
    
    /**
     * Tweaker used to notify the environment when we transition from preinit to
     * default
     */
    public static class EnvironmentStateTweaker implements ITweaker {

        @Override
        public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
        }

        @Override
        public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        }

        @Override
        public String getLaunchTarget() {
            return "";
        }

        @Override
        public String[] getLaunchArguments() {
            MixinEnvironment.gotoPhase(Phase.DEFAULT);
            return new String[0];
        }
        
    }

    // Blackboard keys
    private static final String CONFIGS_KEY = "mixin.configs";
    private static final String TRANSFORMER_KEY = "mixin.transformer";
    
    /**
     * Current (active) environment phase, set to NOT_INITIALISED until the
     * phases have been populated
     */
    private static Phase currentPhase = Phase.NOT_INITIALISED;
    
    /**
     * Array of all (real) environments, indexed by ordinal
     */
    private static MixinEnvironment[] environments = new MixinEnvironment[Phase.phases.size()];
    
    /**
     * Currently active environment
     */
    private static MixinEnvironment currentEnvironment;
    
    /**
     * The phase for this environment
     */
    private final Phase phase;
    
    /**
     * The blackboard key for this environment's configs
     */
    private final String configsKey;
    
    /**
     * This environment's options
     */
    private final boolean[] options;
    
    /**
     * Detected side 
     */
    private Side side;
    
    private MixinEnvironment(Phase phase) {
        this.phase = phase;
        this.configsKey = MixinEnvironment.CONFIGS_KEY + "." + this.phase.name.toLowerCase();
        
        // Sanity check
        Object version = Launch.blackboard.get(MixinBootstrap.INIT_KEY);
        if (version == null || !MixinBootstrap.VERSION.equals(version)) {
            throw new RuntimeException("Environment conflict, mismatched versions or you didn't call MixinBootstrap.init()");
        }
        
        // Also sanity check
        if (this.getClass().getClassLoader() != Launch.class.getClassLoader()) {
            throw new RuntimeException("Attempted to init the mixin environment in the wrong classloader");
        }
        
        this.options = new boolean[Option.values().length];
        for (Option option : Option.values()) {
            this.options[option.ordinal()] = option.getValue();
        }
        
        if (this.getOption(Option.DEBUG_VERBOSE) && this.phase == Phase.PREINIT) {
            PrettyPrinter printer = new PrettyPrinter(32);
            printer.add("SpongePowered MIXIN (Verbose debugging enabled)").centre().hr();
            printer.add("%25s : %s", "Code source", this.getClass().getProtectionDomain().getCodeSource().getLocation());
            printer.add("%25s : %s", "Internal Version", version).hr();
            for (Option option : Option.values()) {
                printer.add("%25s : %s%s", option.property, option.parent == null ? "" : " - ", this.getOption(option));
            }
            printer.hr().add("%25s : %s", "Detected Side", this.getSide());
            printer.print(System.err);
        }
    }
    
    /**
     * Get the phase for this environment
     */
    public Phase getPhase() {
        return this.phase;
    }
    
    /**
     * Get mixin configurations from the blackboard
     * 
     * @return list of registered mixin configs
     */
    public List<String> getMixinConfigs() {
        @SuppressWarnings("unchecked")
        List<String> mixinConfigs = (List<String>) Launch.blackboard.get(this.configsKey);
        if (mixinConfigs == null) {
            mixinConfigs = new ArrayList<String>();
            Launch.blackboard.put(this.configsKey, mixinConfigs);
        }
        return mixinConfigs;
    }
    
    /**
     * Add a mixin configuration to the blackboard
     * 
     * @param config Name of configuration resource to add
     * @return fluent interface
     */
    public MixinEnvironment addConfiguration(String config) {
        List<String> configs = this.getMixinConfigs();
        if (!configs.contains(config)) {
            configs.add(config);
        }
        return this;
    }

    /**
     * Get the active mixin transformer instance (if any)
     */
    public Object getActiveTransformer() {
        return Launch.blackboard.get(MixinEnvironment.TRANSFORMER_KEY);
    }

    /**
     * Set the mixin transformer instance
     * 
     * @param transformer Mixin Transformer
     */
    public void setActiveTransformer(IClassTransformer transformer) {
        if (transformer != null) {
            Launch.blackboard.put(MixinEnvironment.TRANSFORMER_KEY, transformer);        
        }
    }
    
    /**
     * Allows a third party to set the side if the side is currently UNKNOWN
     * 
     * @param side Side to set to
     * @return fluent interface
     */
    public MixinEnvironment setSide(Side side) {
        if (side != null && this.getSide() == Side.UNKNOWN && side != Side.UNKNOWN) {
            this.side = side;
        }
        return this;
    }
    
    /**
     * Get (and detect if necessary) the current side  
     * 
     * @return current side (or UNKNOWN if could not be determined)
     */
    public Side getSide() {
        if (this.side == null) {
            for (Side side : Side.values()) {
                if (side.detect()) {
                    this.side = side;
                    break;
                }
            }
        }
        
        return this.side != null ? this.side : Side.UNKNOWN;
    }
    
    /**
     * Get the current mixin subsystem version
     */
    public String getVersion() {
        return (String)Launch.blackboard.get(MixinBootstrap.INIT_KEY);
    }

    /**
     * Get the specified option from the current environment
     * 
     * @param option Option to get
     * @return Option value
     */
    public boolean getOption(Option option) {
        return this.options[option.ordinal()];
    }
    
    /**
     * Set the specified option for this environment
     * 
     * @param option Option to set
     * @param value New option value
     */
    public void setOption(Option option, boolean value) {
        this.options[option.ordinal()] = value;
    }
    
    /**
     * Invoke a mixin environment audit process
     */
    public void audit() {
        Object activeTransformer = this.getActiveTransformer();
        if (activeTransformer instanceof MixinTransformer) {
            MixinTransformer transformer = (MixinTransformer)activeTransformer;
            transformer.audit();
        }
    }
    
    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return String.format("%s[%s]", this.getClass().getSimpleName(), this.phase);
    }
    
    /**
     * Get the current phase, triggers initialisation if necessary
     */
    private static Phase getCurrentPhase() {
        if (MixinEnvironment.currentPhase == Phase.NOT_INITIALISED) {
            MixinEnvironment.init(Phase.PREINIT);
        }
        
        return MixinEnvironment.currentPhase;
    }
    
    /**
     * Initialise the mixin environment in the specified phase
     * 
     * @param phase initial phase
     */
    public static void init(Phase phase) {
        if (MixinEnvironment.currentPhase == Phase.NOT_INITIALISED) {
            MixinEnvironment.currentPhase = phase;
            MixinEnvironment.getEnvironment(phase);
        }
    }
    
    /**
     * Get the mixin environment for the specified phase
     * 
     * @param phase phase to fetch environment for
     * @return the environment
     */
    public static MixinEnvironment getEnvironment(Phase phase) {
        if (phase.ordinal < 0) {
            throw new IllegalArgumentException("Cannot access the UNINITIALISED environment");
        }
        
        if (MixinEnvironment.environments[phase.ordinal] == null) {
            MixinEnvironment.environments[phase.ordinal] = new MixinEnvironment(phase);
        }
        
        return MixinEnvironment.environments[phase.ordinal];
    }

    /**
     * Gets the default environment
     */
    public static MixinEnvironment getDefaultEnvironment() {
        return MixinEnvironment.getEnvironment(Phase.DEFAULT);
    }

    /**
     * Gets the current environment
     */
    public static MixinEnvironment getCurrentEnvironment() {
        if (MixinEnvironment.currentEnvironment == null) {
            MixinEnvironment.currentEnvironment = MixinEnvironment.getEnvironment(MixinEnvironment.getCurrentPhase());
        }
        
        return MixinEnvironment.currentEnvironment;
    }

    /**
     * Internal callback
     * 
     * @param phase
     */
    static void gotoPhase(Phase phase) {
        if (phase == null || phase.ordinal < 0) {
            throw new IllegalArgumentException("Cannot go to the specified phase, phase is null or invalid");
        }
        
        if (phase.ordinal > getCurrentPhase().ordinal) {
            MixinBootstrap.addProxy();
        }
        
        MixinEnvironment.currentPhase = phase;
        MixinEnvironment.currentEnvironment = MixinEnvironment.getEnvironment(MixinEnvironment.getCurrentPhase());
    }
}

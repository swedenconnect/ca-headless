/*
 * Copyright (c) 2022.  Agency for Digital Government (DIGG)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package se.swedenconnect.ca.tools.repomigration.options;

import org.apache.commons.cli.Options;

/**
 * CLI options
 *
 * @author Martin Lindström (martin@idsec.se)
 * @author Stefan Santesson (stefan@idsec.se)
 */
public class AppOptions {
    public static final String OPTION_DIR = "d";
    public static final String OPTION_LIST = "list";
    public static final String OPTION_VERBOSE = "v";
    public static final String OPTION_DB_MERGE = "dbmerge";
    public static final String OPTION_FILE_MERGE = "filemerge";
    public static final String OPTION_LOG = "log";
    public static final String OPTION_HELP = "help";

    private static final Options op;

    static{
        op = new Options();
        op.addOption(OPTION_DIR, true, "Configuration directory for the CA service");
        op.addOption(OPTION_LIST, false, "List available certificates in present repositories");
        op.addOption(OPTION_VERBOSE, false, "Verbose list information");
        op.addOption(OPTION_DB_MERGE, false, "Include this argument to merge certificates in the file repository into the database repository");
        op.addOption(OPTION_FILE_MERGE, false, "Include this argument to merge certificates in the database repository into the file repository");
        op.addOption(OPTION_LOG, false, "Enable display of process logging");
        op.addOption(OPTION_HELP, false, "Print this message");
    }

    public static Options getOptions() {
        return op;
    }
}

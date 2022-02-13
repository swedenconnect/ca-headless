/*
 * Copyright 2017 3xA Security AB
 *  		 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package se.swedenconnect.ca.tools.repomigration.options;

import org.apache.commons.cli.Options;

/**
 *
 * @author stefan
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

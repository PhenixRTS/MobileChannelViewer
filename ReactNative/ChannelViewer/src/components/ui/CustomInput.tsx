/*
 * Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All Rights Reserved.
 *
 * By using this code you agree to the Phenix Terms of Service found online here:
 * http://phenixrts.com/terms-of-service.html
 */

import React from 'react';
import {
  View,
  StyleSheet,
  TextInput,
  TextInputProps,
  TouchableOpacity
} from 'react-native';

import {CloseIcon} from '../../assets/icons';

interface CustomInputProps extends TextInputProps {
  onClear: () => void
}

export const CustomInput = ({value, placeholder, keyboardType, onChangeText, onClear, ...rest}: CustomInputProps) => (
  <View style={styles.root}>
    <TextInput
      value={value}
      placeholder={placeholder || ''}
      keyboardType={keyboardType}
      onChangeText={onChangeText}
      style={styles.input}
      {...rest}
    />
    <View style={styles.clearWrapper}>
      <TouchableOpacity onPress={onClear} style={styles.clearBtn}>
        <CloseIcon width={20} height={20}/>
      </TouchableOpacity>
    </View>
  </View>
);

const styles = StyleSheet.create({
  root: {
    flex: 1,
    position: 'relative'
  },
  input: {
    height: 40,
    borderWidth: 1,
    padding: 8,
    paddingRight: 32,
    color: 'black'
  },
  clearWrapper: {
    position: 'absolute',
    width: 32,
    height: '100%',
    right: 0,
    justifyContent: 'center',
    alignItems: 'center'
  },
  clearBtn: {
    width: 20,
    height: 20,
    justifyContent: 'center',
    alignItems: 'center'
  }
});
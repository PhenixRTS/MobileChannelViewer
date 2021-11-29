/*
 * Copyright 2021 Phenix Real Time Solutions, Inc. Confidential and Proprietary. All Rights Reserved.
 *
 * By using this code you agree to the Phenix Terms of Service found online here:
 * http://phenixrts.com/terms-of-service.html
 */

import React from 'react';
import {
  StyleSheet,
  Button,
  ButtonProps
} from 'react-native';

export const CustomButton = ({title, onPress, ...rest}: ButtonProps) => (
  <Button
    title={title}
    onPress={onPress}
    style={styles.root}
    {...rest}
  />
);

const styles = StyleSheet.create({
  root: {
    flex: 1,
    height: 40,
    margin: 12,
    borderWidth: 1,
    padding: 8,
  },
})
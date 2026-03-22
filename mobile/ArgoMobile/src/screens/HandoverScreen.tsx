import React from 'react';
import { View, Text, StyleSheet } from 'react-native';

const HandoverScreen: React.FC = () => {
  return (
    <View style={styles.container}>
      <Text style={styles.title}>Handover Dashboard</Text>
      <Text>Shift handover logbook here.</Text>
    </View>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  title: { fontSize: 24 },
});

export default HandoverScreen;
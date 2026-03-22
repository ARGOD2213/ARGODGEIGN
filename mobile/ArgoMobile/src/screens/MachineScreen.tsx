import React, { useEffect, useState } from 'react';
import { View, Text, FlatList, StyleSheet, TouchableOpacity } from 'react-native';
import axios from 'axios';

const MachineScreen: React.FC = () => {
  const [machines, setMachines] = useState([]);

  useEffect(() => {
    axios.get('http://your-ec2-ip:8080/api/v1/machines') // Replace with actual API
      .then(response => setMachines(response.data))
      .catch(error => console.error(error));
  }, []);

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Machine Dashboard</Text>
      <FlatList
        data={machines}
        keyExtractor={(item: any) => item.id}
        renderItem={({ item }: any) => (
          <TouchableOpacity style={styles.item}>
            <Text>{item.name}</Text>
            <Text>Status: {item.status}</Text>
          </TouchableOpacity>
        )}
      />
    </View>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1, padding: 20 },
  title: { fontSize: 24, marginBottom: 20 },
  item: { padding: 10, borderBottomWidth: 1, borderColor: '#ccc' },
});

export default MachineScreen;